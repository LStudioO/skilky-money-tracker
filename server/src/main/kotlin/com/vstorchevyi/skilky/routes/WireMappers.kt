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
    val input = InputType.entries.firstOrNull { it.name == expense.inputType } ?: InputType.TEXT
    val currency = Currency.fromCode(expense.currency) ?: Currency.UAH
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
