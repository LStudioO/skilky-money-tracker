package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.api.CreateCategoryRequest
import com.vstorchevyi.skilky.api.UpdateCategoryRequest
import com.vstorchevyi.skilky.data.local.CategoryDao
import com.vstorchevyi.skilky.data.local.CategoryEntity
import com.vstorchevyi.skilky.data.local.asStorageResult
import com.vstorchevyi.skilky.data.local.runCatchingStorage
import com.vstorchevyi.skilky.data.mapper.toDomain
import com.vstorchevyi.skilky.data.mapper.toEntity
import com.vstorchevyi.skilky.data.remote.CategoryApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.model.flatMap
import com.vstorchevyi.skilky.domain.model.map
import com.vstorchevyi.skilky.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * Cache-first wiring of [CategoryRepository]:
 * - `getCategories()` returns the DAO Flow, so the UI never waits on the network.
 * - `refresh()` GETs the full list and replaces the local cache.
 * - `create/update/delete` write to the server first; the local cache
 *   mirrors the result on success and is left untouched on failure.
 *
 * Transport failures are mapped by [runCatchingApi]. Room failures are kept
 * separate by [runCatchingStorage].
 */
internal class CategoryRepositoryImpl(
    private val dao: CategoryDao,
    private val api: CategoryApi,
    private val clock: Clock = Clock.System,
) : CategoryRepository {
    override fun getCategories(): Flow<Either<AppError, List<Category>>> =
        dao.getAll()
            .map { it.map(CategoryEntity::toDomain) }
            .asStorageResult()

    override suspend fun refresh(): Either<AppError, Unit> =
        runCatchingApi {
            val now = clock.now().toEpochMilliseconds()
            api.list().map { it.toEntity(updatedAt = now) }
        }.flatMap { entities ->
            runCatchingStorage { dao.replaceAll(entities) }
        }

    override suspend fun create(
        name: String,
        icon: String,
        color: String,
    ): Either<AppError, Category> =
        runCatchingApi {
            api.create(CreateCategoryRequest(name = name, icon = icon, color = color))
        }.flatMap { dto ->
            val entity = dto.toEntity(updatedAt = clock.now().toEpochMilliseconds())
            runCatchingStorage { dao.upsertAll(listOf(entity)) }
                .map { dto.toDomain() }
        }

    override suspend fun update(
        id: Long,
        name: String,
        icon: String,
        color: String,
    ): Either<AppError, Category> =
        runCatchingApi {
            api.update(id, UpdateCategoryRequest(name = name, icon = icon, color = color))
        }.flatMap { dto ->
            val entity = dto.toEntity(updatedAt = clock.now().toEpochMilliseconds())
            runCatchingStorage { dao.upsertAll(listOf(entity)) }
                .map { dto.toDomain() }
        }

    override suspend fun delete(id: Long): Either<AppError, Unit> =
        runCatchingApi { api.delete(id) }
            .flatMap { runCatchingStorage { dao.deleteById(id) } }
}
