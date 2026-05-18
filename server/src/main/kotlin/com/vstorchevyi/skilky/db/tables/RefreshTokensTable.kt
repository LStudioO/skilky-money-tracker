package com.vstorchevyi.skilky.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object RefreshTokensTable : LongIdTable("refresh_tokens") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", length = 512).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
