package com.vstorchevyi.skilky.api

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ExpenseRequest(
    val name: String,
    /**
     * Expense amount in major units. `Double` keeps the wire format simple
     * for the mobile clients, at the cost of precision past roughly 15
     * decimal digits — fine for everyday spending, risky for amounts above
     * a few trillion. The server rounds to two decimals on insert. When the
     * app starts handling larger amounts (or stricter currencies than UAH /
     * USD), promote this field to a string-encoded decimal.
     */
    val amount: Double,
    val currency: Currency,
    val categoryId: Long,
    val note: String? = null,
    val inputType: InputType,
    val clientId: String,
    @Serializable(with = LocalDateIsoSerializer::class)
    val date: LocalDate,
)

@Serializable
data class ExpenseBatchRequest(
    val items: List<ExpenseRequest>,
)

@Serializable
data class ExpenseResponse(
    val id: Long,
    val name: String,
    val amount: Double,
    val currency: Currency,
    val category: CategoryDto,
    val note: String?,
    val inputType: InputType,
    @Serializable(with = LocalDateIsoSerializer::class)
    val date: LocalDate,
    @Serializable(with = InstantIsoSerializer::class)
    val createdAt: Instant,
)

@Serializable
data class ExpenseListResponse(
    val items: List<ExpenseResponse>,
    val total: Int,
    val page: Int,
    val size: Int,
)
