package com.vstorchevyi.skilky.security

import at.favre.lib.crypto.bcrypt.BCrypt

class PasswordHasher(
    private val cost: Int = DEFAULT_COST,
) {
    fun hash(password: String): String = BCrypt.withDefaults().hashToString(cost, password.toCharArray())

    fun verify(
        password: String,
        hash: String,
    ): Boolean = BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    companion object {
        const val DEFAULT_COST = 12
    }
}
