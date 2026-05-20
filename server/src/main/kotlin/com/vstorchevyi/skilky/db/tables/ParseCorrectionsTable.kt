package com.vstorchevyi.skilky.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * One row per parse correction submitted by the client after the user
 * edits the preview. Stores the model's output and the user's final
 * choice as JSON text so prompt regressions can be quantified later
 * without re-running the diff on the client.
 *
 * Items are stored as raw JSON rather than as a relational child table
 * because we never query the items individually here — the entire blob
 * is read together when analyzing corrections offline. Avoiding the
 * join keeps inserts cheap on the hot path.
 */
object ParseCorrectionsTable : LongIdTable("parse_corrections") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val modality = varchar("modality", length = 16)
    val currency = varchar("currency", length = 3)
    val itemsOriginal = text("items_original")
    val itemsFinal = text("items_final")
    val capturedAt = timestamp("captured_at").defaultExpression(CurrentTimestamp)
}
