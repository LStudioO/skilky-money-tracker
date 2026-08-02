package com.vstorchevyi.skilky.domain.model

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.InputType
import kotlinx.datetime.LocalDate

/**
 * The shape the UI hands to the repository when creating or editing an
 * expense. [inputType] records how the draft originated. The repository
 * generates the client id when it builds the request.
 */
data class ExpenseInput(
    val name: String,
    val amount: Double,
    val currency: Currency,
    val categoryId: Long,
    val note: String?,
    val date: LocalDate,
    val inputType: InputType = InputType.TEXT,
)
