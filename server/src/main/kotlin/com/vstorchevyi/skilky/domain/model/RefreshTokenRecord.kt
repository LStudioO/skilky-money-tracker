package com.vstorchevyi.skilky.domain.model

import kotlin.time.Instant

/**
 * One row in the `refresh_tokens` table.
 *
 * @property tokenHash HMAC-SHA256 of the raw token. The raw value lives only
 *   in the response to the client at issue time and the inbound request body
 *   on `/auth/refresh`. Persisting only the hash means a DB dump alone
 *   cannot be used to impersonate users.
 */
data class RefreshTokenRecord(
    val id: Long,
    val userId: Long,
    val tokenHash: String,
    val expiresAt: Instant,
)
