package com.vstorchevyi.skilky.data.mapper

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ExpenseResponse
import com.vstorchevyi.skilky.api.InputType
import com.vstorchevyi.skilky.data.local.ExpenseEntity
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseCategorySnapshot
import kotlinx.datetime.LocalDate

/**
 * Turns the wire DTO into the on-disk entity. The category fields are
 * denormalized onto the row; enums are stored by name; the `LocalDate` flat-
 * tens to ISO-8601.
 */
internal fun ExpenseResponse.toEntity(): ExpenseEntity =
    ExpenseEntity(
        id = id,
        name = name,
        amount = amount,
        currency = currency.name,
        categoryId = category.id,
        categoryName = category.name,
        categoryIcon = category.icon,
        categoryColor = category.color,
        note = note,
        inputType = inputType.name,
        dateIso = date.toString(),
        createdAtMillis = createdAt.toEpochMilliseconds(),
    )

internal fun ExpenseEntity.toDomain(): Expense =
    Expense(
        id = id,
        name = name,
        amount = amount,
        currency = Currency.valueOf(currency),
        category =
            ExpenseCategorySnapshot(
                id = categoryId,
                name = categoryName,
                icon = categoryIcon,
                color = categoryColor,
            ),
        note = note,
        inputType = InputType.valueOf(inputType),
        date = LocalDate.parse(dateIso),
        createdAt = kotlin.time.Instant.fromEpochMilliseconds(createdAtMillis),
    )
