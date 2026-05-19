package com.vstorchevyi.skilky.security

import com.vstorchevyi.skilky.api.ExpenseBatchRequest
import com.vstorchevyi.skilky.api.ExpenseRequest
import com.vstorchevyi.skilky.errors.ValidationException
import kotlinx.datetime.LocalDate

private val CLIENT_ID_UUID =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

private const val EXPENSE_NAME_MAX = 255
private const val MAX_BATCH = 100

/**
 * Validates a single expense line item. **Throws** [ValidationException] for the `422` envelope.
 */
fun validateExpenseRequest(req: ExpenseRequest) {
    val name = req.name.trim()
    if (name.isEmpty() || name.length > EXPENSE_NAME_MAX) {
        throw ValidationException("Expense name must be 1-$EXPENSE_NAME_MAX characters")
    }
    if (!req.amount.isFinite() || req.amount <= 0.0) {
        throw ValidationException("Amount must be a positive number")
    }
    val client = req.clientId.trim()
    if (!CLIENT_ID_UUID.matches(client)) {
        throw ValidationException("clientId must be a canonical UUID string")
    }
}

/**
 * Validates a batch envelope and each nested item via [validateExpenseRequest].
 */
fun validateExpenseBatch(req: ExpenseBatchRequest) {
    if (req.items.isEmpty()) {
        throw ValidationException("At least one expense item is required")
    }
    if (req.items.size > MAX_BATCH) {
        throw ValidationException("At most $MAX_BATCH items per request")
    }
    req.items.forEach(::validateExpenseRequest)
}

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

/** Validates custom category fields from POST/PUT bodies. */
fun validateCreateCategory(
    name: String,
    icon: String,
    color: String,
) {
    val n = name.trim()
    if (n.isEmpty() || n.length > 100) {
        throw ValidationException("Category name must be 1-100 characters")
    }
    val ic = icon.trim()
    if (ic.isEmpty() || ic.length > 80) {
        throw ValidationException("Category icon must be 1-80 characters")
    }
    val c = color.trim()
    if (!HEX_COLOR.matches(c)) {
        throw ValidationException("Category color must be a #RRGGBB hex value")
    }
}

/** Parses `YYYY-MM-DD` query params; blank means absent; malformed throws [ValidationException]. */
fun parseLocalDateOrThrow(
    raw: String?,
    paramName: String,
): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(raw) }
        .getOrElse { throw ValidationException("Invalid $paramName date (expected YYYY-MM-DD)") }
}
