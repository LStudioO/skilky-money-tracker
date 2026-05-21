package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.ParseModality
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.ParseCorrectionsTable
import org.jetbrains.exposed.v1.jdbc.insert

/**
 * Insert-only repository for parse-correction telemetry. The original
 * model output and the user's final edit are passed in as already-
 * serialized JSON so the route handler can encode once and the repository
 * stays JSON-shape agnostic.
 */
class ParseCorrectionsRepository(
    private val databaseFactory: DatabaseFactory,
) {
    suspend fun insert(
        userId: Long,
        modality: ParseModality,
        currency: Currency,
        itemsOriginalJson: String,
        itemsFinalJson: String,
    ): Long =
        databaseFactory.dbQuery {
            ParseCorrectionsTable.insert {
                it[ParseCorrectionsTable.userId] = userId
                it[ParseCorrectionsTable.modality] = modality.name
                it[ParseCorrectionsTable.currency] = currency.code
                it[ParseCorrectionsTable.itemsOriginal] = itemsOriginalJson
                it[ParseCorrectionsTable.itemsFinal] = itemsFinalJson
            }[ParseCorrectionsTable.id].value
        }
}
