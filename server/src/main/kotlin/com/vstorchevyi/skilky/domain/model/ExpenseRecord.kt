package com.vstorchevyi.skilky.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class ExpenseRecord(
    val id: Long,
    val userId: Long,
    val categoryId: Long,
    val name: String,
    val amount: java.math.BigDecimal,
    val currency: String,
    val note: String?,
    val inputType: String,
    val clientId: String,
    val date: LocalDate,
    val createdAt: Instant,
)
