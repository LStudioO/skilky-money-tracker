package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.data.local.ExpenseDao
import com.vstorchevyi.skilky.data.mapper.toDomain
import com.vstorchevyi.skilky.data.mapper.toEntity
import com.vstorchevyi.skilky.data.remote.ExpenseApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.Expense
import com.vstorchevyi.skilky.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Cache-first wiring of [ExpenseRepository]: `observe()` returns the DAO
 * Flow so the UI never waits on the network; `refresh()` replaces the local
 * cache with the latest server page. Transport / HTTP failure handling
 * lives in [runCatchingApi].
 */
internal class ExpenseRepositoryImpl(
    private val dao: ExpenseDao,
    private val api: ExpenseApi,
) : ExpenseRepository {
    override fun observe(): Flow<List<Expense>> = dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refresh(): Either<AppError, Unit> =
        runCatchingApi {
            val response = api.list()
            val entities = response.items.map { it.toEntity() }
            dao.clear()
            dao.upsertAll(entities)
            Unit
        }
}
