package com.vstorchevyi.skilky.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Hashes refresh tokens before they are written to the database.
 *
 * **Why hash refresh tokens at all.** A refresh token is a long-lived
 * credential. A database dump (SQL injection elsewhere, leaked backup,
 * insider access) of raw tokens lets an attacker mint access tokens for
 * every user. Storing only a hash keeps a DB-only compromise useless.
 *
 * **Why HMAC and not bare SHA-256.** The keyed construction means an
 * attacker with the DB alone cannot verify guesses without also stealing
 * the [pepper] from application config. The pepper lives in env vars /
 * HOCON, not in the database, so the two surfaces have to be breached
 * separately.
 *
 * **Why not BCrypt.** BCrypt is deliberately slow (~250 ms) because it
 * defends low-entropy human passwords against offline guessing. Refresh
 * tokens are server-generated UUIDs with 122 bits of entropy; slowing
 * comparison adds no security and turns every `/auth/refresh` into a CPU
 * burn.
 *
 * **Lookup pattern.** HMAC is deterministic, so we put a unique index on
 * the hash column and look up `O(log n)` exactly as we would for raw
 * tokens.
 *
 * @param pepper Application-wide secret used as the HMAC key. Distinct
 *   from the JWT signing secret on purpose: one key per cryptographic use,
 *   so a leak of one surface does not cascade into the other.
 */
class TokenHasher(
    pepper: String,
) {
    private val key = SecretKeySpec(pepper.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)

    /** Compute the storable hash for [token]. Hex-encoded for varchar friendliness. */
    fun hash(token: String): String {
        // Mac instances are not thread-safe; build one per call.
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(key)
        val bytes = mac.doFinal(token.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
