package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.api.Currency
import com.vstorchevyi.skilky.api.InputType
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseCategorySnapshot
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Test double for [ExpenseRepository]. `getExpenses()` is a `MutableStateFlow`
 * so tests can push fresh values to simulate the DAO emitting an update.
 * Mutation methods record their args on [calls] and apply the result to the
 * local list so an `Either.Right` flows back through `getExpenses()`.
 */
class FakeExpenseRepository(
    initial: List<Expense> = emptyList(),
) : ExpenseRepository {
    val calls: MutableList<Call> = mutableListOf()

    private val expenses = MutableStateFlow(initial)

    var refreshResult: Either<AppError, Unit> = Either.Right(Unit)
    var createResult: Either<AppError, Expense> = Either.Right(defaultExpense())
    var updateResult: Either<AppError, Expense> = Either.Right(defaultExpense())
    var deleteResult: Either<AppError, Unit> = Either.Right(Unit)

    override fun getExpenses(): Flow<List<Expense>> = expenses.asStateFlow()

    override fun getExpense(id: Long): Flow<Expense?> = expenses.asStateFlow().map { list -> list.firstOrNull { it.id == id } }

    override suspend fun refresh(): Either<AppError, Unit> {
        calls += Call.Refresh
        return refreshResult
    }

    override suspend fun create(input: ExpenseInput): Either<AppError, Expense> {
        calls += Call.Create(input)
        if (createResult is Either.Right) {
            val created = (createResult as Either.Right<Expense>).value
            expenses.update { it + created }
        }
        return createResult
    }

    override suspend fun update(
        id: Long,
        input: ExpenseInput,
    ): Either<AppError, Expense> {
        calls += Call.Update(id, input)
        if (updateResult is Either.Right) {
            val updated = (updateResult as Either.Right<Expense>).value
            expenses.update { list -> list.map { if (it.id == id) updated else it } }
        }
        return updateResult
    }

    override suspend fun delete(id: Long): Either<AppError, Unit> {
        calls += Call.Delete(id)
        if (deleteResult is Either.Right) {
            expenses.update { list -> list.filterNot { it.id == id } }
        }
        return deleteResult
    }

    fun setExpenses(value: List<Expense>) {
        expenses.value = value
    }

    sealed interface Call {
        data object Refresh : Call

        data class Create(
            val input: ExpenseInput,
        ) : Call

        data class Update(
            val id: Long,
            val input: ExpenseInput,
        ) : Call

        data class Delete(
            val id: Long,
        ) : Call
    }

    companion object {
        fun defaultExpense(
            id: Long = 0,
            name: String = "Default",
            amount: Double = 0.0,
            categoryId: Long = 1,
            date: LocalDate = LocalDate(1970, 1, 1),
        ): Expense =
            Expense(
                id = id,
                name = name,
                amount = amount,
                currency = Currency.UAH,
                category =
                    ExpenseCategorySnapshot(
                        id = categoryId,
                        name = "Food",
                        icon = "🍎",
                        color = "#FF6B6B",
                    ),
                note = null,
                inputType = InputType.TEXT,
                date = date,
                createdAt = Instant.fromEpochMilliseconds(0),
            )
    }
}
