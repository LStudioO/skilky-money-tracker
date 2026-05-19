package com.vstorchevyi.skilky.routes

import com.vstorchevyi.skilky.api.CategoryDto
import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.DefaultCategoryTranslations
import com.vstorchevyi.skilky.api.ExpenseResponse
import com.vstorchevyi.skilky.api.InputType
import com.vstorchevyi.skilky.domain.model.CategoryRecord
import com.vstorchevyi.skilky.repository.ExpenseWithCategory

/**
 * Maps persistence records to wire DTOs. **Localization:** when `name_key` is set, the API
 * substitutes [DefaultCategoryTranslations] using the request's normalized `Accept-Language`;
 * user-created rows keep the stored [CategoryRecord.name] verbatim.
 */
fun CategoryRecord.toDto(languageTag: String): CategoryDto =
    CategoryDto(
        id = id,
        name = DefaultCategoryTranslations.displayName(nameKey, name, languageTag),
        icon = icon,
        color = color,
        isDefault = isDefault,
        nameKey = nameKey,
    )

fun ExpenseWithCategory.toResponse(languageTag: String): ExpenseResponse {
    // A row holding a value outside our enums means stored data has drifted
    // from the application's contract — never expected, never papered over.
    // Throwing lets StatusPages surface a 500 with the row id so the bad
    // record is identifiable in logs rather than silently rewritten.
    val input =
        InputType.entries.firstOrNull { it.name == expense.inputType }
            ?: error("Expense ${expense.id} has unknown inputType '${expense.inputType}'")
    val currency =
        Currency.fromCode(expense.currency)
            ?: error("Expense ${expense.id} has unknown currency '${expense.currency}'")
    return ExpenseResponse(
        id = expense.id,
        name = expense.name,
        amount = expense.amount.toDouble(),
        currency = currency,
        category = category.toDto(languageTag),
        note = expense.note,
        inputType = input,
        date = expense.date,
        createdAt = expense.createdAt,
    )
}
