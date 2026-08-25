package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.api.ExpenseBatchRequest
import com.vstorchevyi.skilky.api.ExpenseRequest
import com.vstorchevyi.skilky.data.local.CategoryDao
import com.vstorchevyi.skilky.data.local.CategoryEntity
import com.vstorchevyi.skilky.data.local.ExpenseDao
import com.vstorchevyi.skilky.data.local.ExpenseEntity
import com.vstorchevyi.skilky.data.local.SyncQueueDao
import com.vstorchevyi.skilky.data.local.SyncQueueEntity
import com.vstorchevyi.skilky.data.local.SyncQueueStatus
import com.vstorchevyi.skilky.data.local.asStorageResult
import com.vstorchevyi.skilky.data.local.runCatchingStorage
import com.vstorchevyi.skilky.data.mapper.toDomain
import com.vstorchevyi.skilky.data.mapper.toEntity
import com.vstorchevyi.skilky.data.remote.ExpenseApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.model.ExpenseInput
import com.vstorchevyi.skilky.domain.model.ExpenseSyncStatus
import com.vstorchevyi.skilky.domain.model.flatMap
import com.vstorchevyi.skilky.domain.model.map
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Cache-first wiring of [ExpenseRepository]:
 * - `getExpenses()` / `getExpense(id)` return DAO Flows, so the UI never
 *   waits on the network.
 * - `refresh()` uploads queued creates, then replaces synced cache rows with
 *   the latest server page while preserving pending local rows.
 * - `create` writes a pending row and its request to Room first, then makes a
 *   best-effort upload. Update and delete remain online-only for this phase.
 *
 * The server's `POST /expenses` is a batch endpoint that deduplicates on
 * `(user_id, client_id)`. A single-item create wraps in a one-element batch.
 * The queue keeps one stable [ExpenseRequest.clientId] per create. Retrying a
 * request is safe because the server deduplicates on `(user_id, client_id)`.
 */
@OptIn(ExperimentalUuidApi::class)
internal class ExpenseRepositoryImpl(
    private val dao: ExpenseDao,
    private val categoryDao: CategoryDao,
    private val syncQueueDao: SyncQueueDao,
    private val api: ExpenseApi,
    private val clientIdFactory: () -> String = { Uuid.random().toString() },
    private val clock: Clock = Clock.System,
) : ExpenseRepository {
    private val syncMutex = Mutex()

    override fun getExpenses(): Flow<Either<AppError, List<Expense>>> =
        combine(dao.getAll(), syncQueueDao.observeAll()) { expenses, queued ->
            expenses.map { entity -> entity.toDomain(queued.statusFor(entity.id)) }
        }
            .asStorageResult()

    override fun getExpense(id: Long): Flow<Either<AppError, Expense?>> =
        combine(dao.getById(id), syncQueueDao.observeAll()) { expense, queued ->
            expense?.toDomain(queued.statusFor(expense.id))
        }
            .asStorageResult()

    override suspend fun refresh(): Either<AppError, Unit> {
        syncPending()
        return runCatchingApi {
            val response = api.list()
            response.items.map { it.toEntity() }
        }.flatMap { entities ->
            runCatchingStorage { dao.replaceSynced(entities) }
        }
    }

    override suspend fun syncPending(): Either<AppError, Unit> =
        syncMutex.withLock {
            val queued =
                when (
                    val result =
                        runCatchingStorage {
                            syncQueueDao.resetProcessing()
                            syncQueueDao.getPending(SYNC_BATCH_SIZE)
                        }
                ) {
                    is Either.Left -> return@withLock result
                    is Either.Right -> result.value
                }

            for (item in queued) {
                val processing = runCatchingStorage { syncQueueDao.markProcessing(item.id) }
                if (processing is Either.Left) return@withLock processing

                when (val uploaded = upload(item)) {
                    is Either.Left -> {
                        val failure =
                            runCatchingStorage {
                                val status =
                                    if (item.retryCount + 1 >= MAX_SYNC_RETRIES) {
                                        SyncQueueStatus.FAILED
                                    } else {
                                        SyncQueueStatus.PENDING
                                    }
                                syncQueueDao.recordFailure(item.id, status.name)
                            }
                        return@withLock if (failure is Either.Left) failure else uploaded
                    }

                    is Either.Right -> {
                        val completed =
                            runCatchingStorage {
                                syncQueueDao.complete(
                                    queueId = item.id,
                                    localExpenseId = item.localExpenseId,
                                    syncedExpense = uploaded.value,
                                )
                            }
                        if (completed is Either.Left) return@withLock completed
                    }
                }
            }
            Either.Right(Unit)
        }

    override suspend fun retryPending(id: Long): Either<AppError, Unit> {
        val reset = runCatchingStorage { syncQueueDao.retry(id) }
        return if (reset is Either.Left) reset else syncPending()
    }

    override suspend fun deletePending(id: Long): Either<AppError, Unit> {
        val result = runCatchingStorage { syncQueueDao.deleteQueuedExpense(id) }
        return result
    }

    override suspend fun create(input: ExpenseInput): Either<AppError, Expense> {
        val result = createAll(listOf(input))
        return result.map { it.single() }
    }

    override suspend fun createAll(inputs: List<ExpenseInput>): Either<AppError, List<Expense>> {
        if (inputs.isEmpty()) return Either.Right(emptyList())
        val createdAtMillis = clock.now().toEpochMilliseconds()
        val pending =
            runCatchingStorage {
                inputs.mapIndexed { index, input ->
                    val clientId = clientIdFactory()
                    val localId = localId(clientId)
                    val category = requireNotNull(categoryDao.getById(input.categoryId))
                    val request = input.toRequest(clientId)
                    val entity = request.toPendingEntity(localId, category, createdAtMillis + index)
                    SyncQueueEntity(
                        clientId = clientId,
                        localExpenseId = localId,
                        name = request.name,
                        amount = request.amount,
                        currency = request.currency.name,
                        categoryId = request.categoryId,
                        note = request.note,
                        inputType = request.inputType.name,
                        dateIso = request.date.toString(),
                        createdAtMillis = createdAtMillis + index,
                    ) to entity
                }
            }
        return pending.flatMap { pairs ->
            runCatchingStorage {
                syncQueueDao.enqueueAndCache(
                    queueItems = pairs.map { it.first },
                    expenses = pairs.map { it.second },
                )
            }.map {
                syncPending()
                pairs.map { it.second.toDomain() }
            }
        }
    }

    private suspend fun upload(item: SyncQueueEntity): Either<AppError, ExpenseEntity> =
        runCatchingApi {
            val response = api.createBatch(ExpenseBatchRequest(items = listOf(item.toRequest())))
            check(response.items.size == 1) { "Expense batch response size does not match request" }
            response.items.single().toEntity()
        }

    override suspend fun update(
        id: Long,
        input: ExpenseInput,
    ): Either<AppError, Expense> =
        runCatchingApi {
            api.update(id, input.toRequest(clientIdFactory())).toEntity()
        }.flatMap { entity ->
            runCatchingStorage { dao.upsertAll(listOf(entity)) }
                .map { entity.toDomain() }
        }

    override suspend fun delete(id: Long): Either<AppError, Unit> =
        runCatchingApi { api.delete(id) }
            .flatMap {
                runCatchingStorage { dao.deleteById(id) }
            }

    private fun ExpenseInput.toRequest(clientId: String): ExpenseRequest =
        ExpenseRequest(
            name = name,
            amount = amount,
            currency = currency,
            categoryId = categoryId,
            note = note,
            inputType = inputType,
            clientId = clientId,
            date = date,
        )

    private fun SyncQueueEntity.toRequest(): ExpenseRequest =
        ExpenseRequest(
            name = name,
            amount = amount,
            currency = com.vstorchevyi.skilky.api.Currency.valueOf(currency),
            categoryId = categoryId,
            note = note,
            inputType = com.vstorchevyi.skilky.api.InputType.valueOf(inputType),
            clientId = clientId,
            date = LocalDate.parse(dateIso),
        )

    private fun ExpenseRequest.toPendingEntity(
        localId: Long,
        category: CategoryEntity,
        createdAtMillis: Long,
    ): ExpenseEntity =
        ExpenseEntity(
            id = localId,
            name = name,
            amount = amount,
            currency = currency.name,
            categoryId = categoryId,
            categoryName = category.name,
            categoryIcon = category.icon,
            categoryColor = category.color,
            note = note,
            inputType = inputType.name,
            dateIso = date.toString(),
            createdAtMillis = createdAtMillis,
        )

    private fun localId(clientId: String): Long {
        val hash = clientId.fold(1_125_899_906_842_597L) { value, char -> value * 31 + char.code }
        val value = hash and Long.MAX_VALUE
        return -value.coerceAtLeast(1)
    }

    private fun List<SyncQueueEntity>.statusFor(expenseId: Long): ExpenseSyncStatus =
        firstOrNull { it.localExpenseId == expenseId }
            ?.status
            ?.let(ExpenseSyncStatus::valueOf)
            ?: if (expenseId < 0) ExpenseSyncStatus.PENDING else ExpenseSyncStatus.SYNCED

    private companion object {
        const val SYNC_BATCH_SIZE = 50
        const val MAX_SYNC_RETRIES = 5
    }
}
