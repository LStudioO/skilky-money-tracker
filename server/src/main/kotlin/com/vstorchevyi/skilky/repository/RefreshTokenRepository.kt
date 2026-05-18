package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.RefreshTokensTable
import com.vstorchevyi.skilky.domain.model.RefreshTokenRecord
import com.vstorchevyi.skilky.security.TokenHasher
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Persists refresh tokens. Raw values are never stored; see [TokenHasher].
 *
 * [create] returns the raw token because that is the only chance to hand it
 * to the client. If the response is lost in transit, the token is
 * unrecoverable for that session and the user must log in again.
 */
class RefreshTokenRepository(
    private val databaseFactory: DatabaseFactory,
    private val tokenHasher: TokenHasher,
) {
    /** Issue and persist a new refresh token. Returns the raw value to send to the client. */
    suspend fun create(
        userId: Long,
        ttlDays: Int,
    ): String =
        databaseFactory.dbQuery {
            val raw = UUID.randomUUID().toString()
            val expiresAt = Clock.System.now().plus(ttlDays.days)
            RefreshTokensTable.insert {
                it[RefreshTokensTable.userId] = userId
                it[RefreshTokensTable.token] = tokenHasher.hash(raw)
                it[RefreshTokensTable.expiresAt] = expiresAt
            }
            raw
        }

    /**
     * Look up a refresh token by its raw value.
     *
     * **Token rotation race we accept on purpose.** The route handler will
     * delete this row, create a new one, and respond. If the response is
     * lost between commit and the client receiving it, the client will
     * retry with the OLD raw token, which has no row, and get 401. The
     * user must log in again. The alternative is a grace window (keep the
     * old hash valid for ~30 s), which adds attack surface for replay.
     * Deferred until we see real-world pain.
     */
    suspend fun findValid(rawToken: String): RefreshTokenRecord? =
        databaseFactory.dbQuery {
            val hash = tokenHasher.hash(rawToken)
            val now = Clock.System.now()
            RefreshTokensTable
                .selectAll()
                .where {
                    (RefreshTokensTable.token eq hash) and (RefreshTokensTable.expiresAt greater now)
                }.map { it.toRecord() }
                .singleOrNull()
        }

    suspend fun delete(id: Long) {
        databaseFactory.dbQuery {
            RefreshTokensTable.deleteWhere { RefreshTokensTable.id eq id }
        }
    }

    /**
     * Revoke every refresh token belonging to [userId]. Used by future
     * `/auth/change-password` to invalidate sessions after a credential
     * change.
     */
    suspend fun deleteAllForUser(userId: Long) {
        databaseFactory.dbQuery {
            RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }
        }
    }

    private fun ResultRow.toRecord(): RefreshTokenRecord =
        RefreshTokenRecord(
            id = this[RefreshTokensTable.id].value,
            userId = this[RefreshTokensTable.userId].value,
            tokenHash = this[RefreshTokensTable.token],
            expiresAt = this[RefreshTokensTable.expiresAt],
        )
}
