package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.CategoriesTable
import com.vstorchevyi.skilky.db.tables.ExpensesTable
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.math.BigDecimal

/** One category's aggregated spend over a date range. */
data class CategorySpend(
    val categoryId: Long,
    val categoryName: String,
    /** Stable key for seeded defaults; lets the route localize the display name. */
    val categoryNameKey: String?,
    val total: BigDecimal,
    val count: Long,
)

/**
 * Read-only aggregate queries over the authenticated user's expenses.
 *
 * Every query filters on `currency`: amounts in different currencies are never
 * summed together, since the column stores major units with no conversion.
 */
class AnalyticsRepository(
    private val databaseFactory: DatabaseFactory,
) {
    /**
     * Spend per category for expenses dated within `[fromDate, toDate]` (both
     * inclusive). Categories with no expenses in the range are absent. The
     * category names are resolved in a second query against the id set, the
     * same shape [ExpenseRepository.list] uses.
     */
    suspend fun spendByCategory(
        userId: Long,
        currency: Currency,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<CategorySpend> =
        databaseFactory.dbQuery {
            val amount = ExpensesTable.amount.sum()
            val rowCount = ExpensesTable.id.count()
            val aggregates =
                ExpensesTable
                    .select(ExpensesTable.categoryId, amount, rowCount)
                    .where { rangeFilter(userId, currency, fromDate, toDate) }
                    .groupBy(ExpensesTable.categoryId)
                    .map { row ->
                        Triple(
                            row[ExpensesTable.categoryId].value,
                            row[amount] ?: BigDecimal.ZERO,
                            row[rowCount],
                        )
                    }
            if (aggregates.isEmpty()) return@dbQuery emptyList()

            val categoriesById =
                CategoriesTable
                    .selectAll()
                    .where { CategoriesTable.id inList aggregates.map { it.first } }
                    .associateBy { it[CategoriesTable.id].value }
            aggregates.mapNotNull { (categoryId, total, count) ->
                val category = categoriesById[categoryId] ?: return@mapNotNull null
                CategorySpend(
                    categoryId = categoryId,
                    categoryName = category[CategoriesTable.name],
                    categoryNameKey = category[CategoriesTable.nameKey],
                    total = total,
                    count = count,
                )
            }
        }

    /**
     * Total spend for each `[from, to]` range, returned in the same order as
     * [ranges]. Used to fill trend buckets; one small aggregate query per
     * bucket keeps the grouping logic in Kotlin instead of DB-specific date
     * functions.
     */
    suspend fun totalsForRanges(
        userId: Long,
        currency: Currency,
        ranges: List<Pair<LocalDate, LocalDate>>,
    ): List<BigDecimal> =
        databaseFactory.dbQuery {
            ranges.map { (from, to) ->
                val amount = ExpensesTable.amount.sum()
                ExpensesTable
                    .select(amount)
                    .where { rangeFilter(userId, currency, from, to) }
                    .firstOrNull()
                    ?.get(amount)
                    ?: BigDecimal.ZERO
            }
        }

    private fun rangeFilter(
        userId: Long,
        currency: Currency,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): Op<Boolean> =
        (ExpensesTable.userId eq userId) and
            (ExpensesTable.currency eq currency.code) and
            (ExpensesTable.date greaterEq fromDate) and
            (ExpensesTable.date lessEq toDate)
}
