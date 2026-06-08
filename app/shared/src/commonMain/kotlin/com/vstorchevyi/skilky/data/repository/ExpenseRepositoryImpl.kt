package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.api.ExpenseBatchRequest
import com.vstorchevyi.skilky.api.ExpenseRequest
import com.vstorchevyi.skilky.api.InputType
import com.vstorchevyi.skilky.data.local.ExpenseDao
import com.vstorchevyi.skilky.data.local.ExpenseEntity
import com.vstorchevyi.skilky.data.mapper.toDomain
import com.vstorchevyi.skilky.data.mapper.toEntity
import com.vstorchevyi.skilky.data.remote.ExpenseApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Cache-first wiring of [ExpenseRepository]:
 * - `getExpenses()` / `getExpense(id)` return DAO Flows, so the UI never
 *   waits on the network.
 * - `refresh()` replaces the local cache with the latest server page.
 * - `create / update / delete` write to the server first; the local cache
 *   mirrors the result on success and is left untouched on failure.
 *
 * The server's `POST /expenses` is a batch endpoint that deduplicates on
 * `(user_id, client_id)`. A single-item create wraps in a one-element batch.
 * The server's [ExpenseRequest.clientId] is required on both POST and PUT;
 * this impl mints a fresh UUID v4 on every call. The trade-off is that an
 * edit changes the row's dedup key, which is fine until Phase 6 introduces
 * an offline queue that needs the original key to survive.
 */
@OptIn(ExperimentalUuidApi::class)
internal class ExpenseRepositoryImpl(
    private val dao: ExpenseDao,
    private val api: ExpenseApi,
    private val clientIdFactory: () -> String = { Uuid.random().toString() },
) : ExpenseRepository {
    override fun getExpenses(): Flow<List<Expense>> = dao.getAll().map { it.map(ExpenseEntity::toDomain) }

    override fun getExpense(id: Long): Flow<Expense?> = dao.getById(id).map { it?.toDomain() }

    override suspend fun refresh(): Either<AppError, Unit> =
        runCatchingApi {
            val response = api.list()
            val entities = response.items.map { it.toEntity() }
            dao.clear()
            dao.upsertAll(entities)
            Unit
        }

    override suspend fun create(input: ExpenseInput): Either<AppError, Expense> =
        runCatchingApi {
            val response = api.createBatch(ExpenseBatchRequest(items = listOf(input.toRequest())))
            val created = response.items.single()
            dao.upsertAll(listOf(created.toEntity()))
            created.toEntity().toDomain()
        }

    override suspend fun update(
        id: Long,
        input: ExpenseInput,
    ): Either<AppError, Expense> =
        runCatchingApi {
            val updated = api.update(id, input.toRequest())
            dao.upsertAll(listOf(updated.toEntity()))
            updated.toEntity().toDomain()
        }

    override suspend fun delete(id: Long): Either<AppError, Unit> =
        runCatchingApi {
            api.delete(id)
            dao.deleteById(id)
            Unit
        }

    private fun ExpenseInput.toRequest(): ExpenseRequest =
        ExpenseRequest(
            name = name,
            amount = amount,
            currency = currency,
            categoryId = categoryId,
            note = note,
            inputType = InputType.TEXT,
            clientId = clientIdFactory(),
            date = date,
        )
}
