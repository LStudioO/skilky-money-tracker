package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.api.ExpenseRequest
import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.CategoriesTable
import com.vstorchevyi.skilky.db.tables.ExpensesTable
import com.vstorchevyi.skilky.domain.model.CategoryRecord
import com.vstorchevyi.skilky.domain.model.ExpenseRecord
import com.vstorchevyi.skilky.errors.ValidationException
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Clock

/** Join of one expense row with its category row for API mapping. */
data class ExpenseWithCategory(
    val expense: ExpenseRecord,
    val category: CategoryRecord,
)

/**
 * Expense reads and writes for the authenticated user scope.
 *
 * **Batch create:** [ExpenseRequest.clientId] is the idempotency key — same user + same UUID
 * updates the existing row instead of inserting again (offline sync retries). **List:** filters
 * are optional; results are paged and ordered by expense date then id descending.
 */
class ExpenseRepository(
    private val databaseFactory: DatabaseFactory,
) {
    suspend fun list(
        userId: Long,
        fromDate: LocalDate?,
        toDate: LocalDate?,
        categoryIdFilter: Long?,
        page: Int,
        size: Int,
    ): Pair<List<ExpenseWithCategory>, Int> =
        databaseFactory.dbQuery {
            val pageIndex = page.coerceAtLeast(0)
            val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)

            fun filter(): Op<Boolean> {
                var cond: Op<Boolean> = ExpensesTable.userId eq userId
                fromDate?.let { d -> cond = cond and (ExpensesTable.date greaterEq d) }
                toDate?.let { d -> cond = cond and (ExpensesTable.date lessEq d) }
                categoryIdFilter?.let { cid -> cond = cond and (ExpensesTable.categoryId eq cid) }
                return cond
            }

            val total =
                ExpensesTable
                    .selectAll()
                    .where { filter() }
                    .count()
                    .toInt()
            val rows =
                ExpensesTable
                    .selectAll()
                    .where { filter() }
                    .orderBy(ExpensesTable.date to SortOrder.DESC, ExpensesTable.id to SortOrder.DESC)
                    .limit(pageSize)
                    .offset((pageIndex * pageSize).toLong())
                    .toList()
            val catIds = rows.map { it[ExpensesTable.categoryId].value }.distinct()
            val categoriesById =
                if (catIds.isEmpty()) {
                    emptyMap()
                } else {
                    CategoriesTable
                        .selectAll()
                        .where { CategoriesTable.id inList catIds }
                        .associate { it[CategoriesTable.id].value to it.toCategoryRecord() }
                }
            val items =
                rows.mapNotNull { row ->
                    val e = row.toExpenseRecord()
                    val c = categoriesById[e.categoryId] ?: return@mapNotNull null
                    ExpenseWithCategory(e, c)
                }
            items to total
        }

    suspend fun findForUser(
        userId: Long,
        expenseId: Long,
    ): ExpenseWithCategory? =
        databaseFactory.dbQuery {
            val row =
                ExpensesTable
                    .selectAll()
                    .where {
                        (ExpensesTable.id eq expenseId) and (ExpensesTable.userId eq userId)
                    }.singleOrNull()
                    ?: return@dbQuery null
            val expense = row.toExpenseRecord()
            val cat = findCategoryRow(expense.categoryId) ?: return@dbQuery null
            ExpenseWithCategory(expense, cat)
        }

    suspend fun findByClientId(
        userId: Long,
        clientId: String,
    ): ExpenseWithCategory? =
        databaseFactory.dbQuery {
            findJoinedByClientId(userId, clientId.trim())
        }

    suspend fun createBatch(
        userId: Long,
        items: List<ExpenseRequest>,
    ): List<ExpenseWithCategory> =
        databaseFactory.dbQuery {
            items.map { req -> upsertExpenseItem(userId, req) }
        }

    private fun upsertExpenseItem(
        userId: Long,
        req: ExpenseRequest,
    ): ExpenseWithCategory {
        val clientId = req.clientId.trim()
        findJoinedByClientId(userId, clientId)?.let { return it }
        val cat =
            findAccessibleCategory(req.categoryId, userId)
                ?: throw ValidationException("Unknown category id ${req.categoryId}")
        return try {
            insertExpenseRow(userId, req, cat, clientId)
        } catch (e: ExposedSQLException) {
            if (e.sqlState != SQL_STATE_UNIQUE_VIOLATION) throw e
            findJoinedByClientId(userId, clientId) ?: throw e
        }
    }

    private fun findJoinedByClientId(
        userId: Long,
        clientId: String,
    ): ExpenseWithCategory? {
        val row =
            ExpensesTable
                .selectAll()
                .where {
                    (ExpensesTable.userId eq userId) and (ExpensesTable.clientId eq clientId)
                }.singleOrNull()
                ?: return null
        val expense = row.toExpenseRecord()
        val category = findCategoryRow(expense.categoryId) ?: return null
        return ExpenseWithCategory(expense, category)
    }

    private fun insertExpenseRow(
        userId: Long,
        req: ExpenseRequest,
        cat: CategoryRecord,
        clientId: String,
    ): ExpenseWithCategory {
        val id =
            ExpensesTable.insert {
                it[ExpensesTable.userId] = userId
                it[ExpensesTable.categoryId] = req.categoryId
                it[ExpensesTable.name] = req.name.trim()
                it[ExpensesTable.amount] = amountOf(req.amount)
                it[ExpensesTable.currency] = req.currency.code
                it[ExpensesTable.note] = req.note?.trim()?.takeIf { s -> s.isNotEmpty() }
                it[ExpensesTable.inputType] = req.inputType.name
                it[ExpensesTable.clientId] = clientId
                it[ExpensesTable.date] = req.date
            }[ExpensesTable.id]
                .value
        val insertedRow =
            ExpensesTable
                .selectAll()
                .where { ExpensesTable.id eq id }
                .single()
        return ExpenseWithCategory(insertedRow.toExpenseRecord(), cat)
    }

    suspend fun update(
        userId: Long,
        expenseId: Long,
        req: ExpenseRequest,
    ): ExpenseWithCategory? =
        databaseFactory.dbQuery {
            val existing =
                ExpensesTable
                    .selectAll()
                    .where {
                        (ExpensesTable.id eq expenseId) and (ExpensesTable.userId eq userId)
                    }.singleOrNull()
                    ?: return@dbQuery null
            val cat =
                findAccessibleCategory(req.categoryId, userId)
                    ?: throw ValidationException("Unknown category id ${req.categoryId}")
            ExpensesTable.update({
                (ExpensesTable.id eq expenseId) and (ExpensesTable.userId eq userId)
            }) {
                it[ExpensesTable.categoryId] = req.categoryId
                it[ExpensesTable.name] = req.name.trim()
                it[ExpensesTable.amount] = amountOf(req.amount)
                it[ExpensesTable.currency] = req.currency.code
                it[ExpensesTable.note] = req.note?.trim()?.takeIf { s -> s.isNotEmpty() }
                it[ExpensesTable.inputType] = req.inputType.name
                it[ExpensesTable.clientId] = req.clientId.trim()
                it[ExpensesTable.date] = req.date
                it[ExpensesTable.updatedAt] = Clock.System.now()
            }
            val row =
                ExpensesTable
                    .selectAll()
                    .where { ExpensesTable.id eq expenseId }
                    .single()
            ExpenseWithCategory(row.toExpenseRecord(), cat)
        }

    suspend fun delete(
        userId: Long,
        expenseId: Long,
    ): Boolean =
        databaseFactory.dbQuery {
            ExpensesTable.deleteWhere {
                (ExpensesTable.id eq expenseId) and (ExpensesTable.userId eq userId)
            } > 0
        }

    private fun findCategoryRow(id: Long): CategoryRecord? =
        CategoriesTable
            .selectAll()
            .where { CategoriesTable.id eq id }
            .singleOrNull()
            ?.toCategoryRecord()

    private fun findAccessibleCategory(
        categoryId: Long,
        userId: Long,
    ): CategoryRecord? =
        CategoriesTable
            .selectAll()
            .where {
                (CategoriesTable.id eq categoryId) and
                    ((CategoriesTable.userId eq null) or (CategoriesTable.userId eq userId))
            }.singleOrNull()
            ?.toCategoryRecord()

    private fun ResultRow.toExpenseRecord(): ExpenseRecord =
        ExpenseRecord(
            id = this[ExpensesTable.id].value,
            userId = this[ExpensesTable.userId].value,
            categoryId = this[ExpensesTable.categoryId].value,
            name = this[ExpensesTable.name],
            amount = this[ExpensesTable.amount],
            currency = this[ExpensesTable.currency],
            note = this[ExpensesTable.note],
            inputType = this[ExpensesTable.inputType],
            clientId = this[ExpensesTable.clientId],
            date = this[ExpensesTable.date],
            createdAt = this[ExpensesTable.createdAt],
        )

    private fun ResultRow.toCategoryRecord(): CategoryRecord =
        CategoryRecord(
            id = this[CategoriesTable.id].value,
            name = this[CategoriesTable.name],
            icon = this[CategoriesTable.icon],
            color = this[CategoriesTable.color],
            isDefault = this[CategoriesTable.isDefault],
            ownerUserId = this[CategoriesTable.userId]?.value,
            nameKey = this[CategoriesTable.nameKey],
        )

    private fun amountOf(raw: Double): BigDecimal = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP)

    companion object {
        private const val MAX_PAGE_SIZE = 100
        private const val SQL_STATE_UNIQUE_VIOLATION = "23505"
    }
}
