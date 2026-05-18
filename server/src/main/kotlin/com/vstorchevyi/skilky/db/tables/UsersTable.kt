package com.vstorchevyi.skilky.db.tables

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object UsersTable : LongIdTable("users") {
    val email = varchar("email", length = 255).uniqueIndex()
    val passwordHash = varchar("password_hash", length = 255)
    val displayName = varchar("display_name", length = 100)
    val defaultCurrency = varchar("default_currency", length = 3).default("UAH")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
