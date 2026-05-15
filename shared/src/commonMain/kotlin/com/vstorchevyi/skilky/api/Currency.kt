package com.vstorchevyi.skilky.api

import kotlinx.serialization.Serializable

@Serializable
enum class Currency(
    val code: String,
    val symbol: String,
) {
    UAH(code = "UAH", symbol = "₴"),
    USD(code = "USD", symbol = "$"),
    EUR(code = "EUR", symbol = "€"),
    GBP(code = "GBP", symbol = "£"),
    ;

    companion object {
        fun fromCode(code: String): Currency? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
