package com.vstorchevyi.skilky.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * One row per logged expense.
 *
 * **Why `client_id` + unique (user_id, client_id):** the mobile client generates a UUID per item
 * before sync; retries and offline queues must not create duplicates. Postgres enforces uniqueness;
 * the repository upserts on conflict.
 */
object ExpensesTable : LongIdTable("expenses") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val categoryId = reference("category_id", CategoriesTable, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", length = 255)
    val amount = decimal("amount", precision = 12, scale = 2)
    val currency = varchar("currency", length = 3)
    val note = text("note").nullable()
    val inputType = varchar("input_type", length = 10)

    /** Canonical UUID string from the client for offline deduplication. */
    val clientId = varchar("client_id", length = 36)
    val date = date("date")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(userId, clientId)
    }
}
