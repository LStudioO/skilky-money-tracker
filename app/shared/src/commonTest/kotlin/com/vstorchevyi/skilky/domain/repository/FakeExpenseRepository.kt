package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test double for [ExpenseRepository]. `getExpenses()` is a `MutableStateFlow`
 * so tests can push fresh values to simulate the DAO emitting an update.
 * `refreshResult` lets tests queue the next outcome.
 */
class FakeExpenseRepository(
    initial: List<Expense> = emptyList(),
) : ExpenseRepository {
    val calls: MutableList<Call> = mutableListOf()

    private val expenses = MutableStateFlow(initial)

    var refreshResult: Either<AppError, Unit> = Either.Right(Unit)

    override fun getExpenses(): Flow<List<Expense>> = expenses.asStateFlow()

    override suspend fun refresh(): Either<AppError, Unit> {
        calls += Call.Refresh
        return refreshResult
    }

    fun setExpenses(value: List<Expense>) {
        expenses.value = value
    }

    sealed interface Call {
        data object Refresh : Call
    }
}
