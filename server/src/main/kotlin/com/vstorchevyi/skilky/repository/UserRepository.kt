package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.UsersTable
import com.vstorchevyi.skilky.domain.model.User
import com.vstorchevyi.skilky.errors.ConflictException
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Data-ring repository for users. Hides Exposed types from callers. */
class UserRepository(
    private val databaseFactory: DatabaseFactory,
) {
    /**
     * Insert a new user.
     *
     * The route handler pre-checks with [findByEmail] before calling this,
     * but no transaction spans the two calls. Two concurrent registrations
     * with the same email can both pass the pre-check, race into INSERT,
     * and the unique index on `email` will fail the loser with Postgres
     * SQLSTATE `23505`. We translate that to [ConflictException] so the
     * loser sees a clean 409 rather than a 500. The database is the only
     * authority that can guarantee uniqueness; the pre-check is just a
     * fast path that avoids a wasted INSERT in the common case.
     *
     * @throws ConflictException if [email] is already taken.
     */
    suspend fun create(
        email: String,
        passwordHash: String,
        displayName: String,
        defaultCurrency: String = DEFAULT_CURRENCY,
    ): User =
        databaseFactory.dbQuery {
            try {
                val id =
                    UsersTable
                        .insert {
                            it[UsersTable.email] = email
                            it[UsersTable.passwordHash] = passwordHash
                            it[UsersTable.displayName] = displayName
                            it[UsersTable.defaultCurrency] = defaultCurrency
                        }[UsersTable.id]
                        .value
                User(
                    id = id,
                    email = email,
                    passwordHash = passwordHash,
                    displayName = displayName,
                    defaultCurrency = defaultCurrency,
                )
            } catch (e: ExposedSQLException) {
                // SQLSTATE 23505 = unique_violation. Branching on sqlState
                // (a SQL-standard 5-char code) keeps the check
                // database-portable rather than parsing driver-specific
                // error strings.
                if (e.sqlState == SQL_STATE_UNIQUE_VIOLATION) {
                    throw ConflictException("Email already registered")
                }
                throw e
            }
        }

    suspend fun findByEmail(email: String): User? =
        databaseFactory.dbQuery {
            UsersTable
                .selectAll()
                .where { UsersTable.email eq email }
                .map { it.toUser() }
                .singleOrNull()
        }

    suspend fun findById(id: Long): User? =
        databaseFactory.dbQuery {
            UsersTable
                .selectAll()
                .where { UsersTable.id eq id }
                .map { it.toUser() }
                .singleOrNull()
        }

    private fun ResultRow.toUser(): User =
        User(
            id = this[UsersTable.id].value,
            email = this[UsersTable.email],
            passwordHash = this[UsersTable.passwordHash],
            displayName = this[UsersTable.displayName],
            defaultCurrency = this[UsersTable.defaultCurrency],
        )

    companion object {
        private const val DEFAULT_CURRENCY = "UAH"
        private const val SQL_STATE_UNIQUE_VIOLATION = "23505"
    }
}
