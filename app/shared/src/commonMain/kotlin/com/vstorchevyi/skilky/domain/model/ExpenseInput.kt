package com.vstorchevyi.skilky.domain.model

import com.vstorchevyi.skilky.api.Currency
import kotlinx.datetime.LocalDate

/**
 * The shape the UI hands to the repository when creating or editing an
 * expense. `inputType` and `clientId` are not part of this contract — both
 * are decided by the repository (manual entry is always TEXT; the clientId
 * is generated as a UUID v4 on each call).
 */
data class ExpenseInput(
    val name: String,
    val amount: Double,
    val currency: Currency,
    val categoryId: Long,
    val note: String?,
    val date: LocalDate,
)
