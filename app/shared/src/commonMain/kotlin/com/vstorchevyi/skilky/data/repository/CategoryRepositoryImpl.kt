package com.vstorchevyi.skilky.data.repository

import com.vstorchevyi.skilky.api.CreateCategoryRequest
import com.vstorchevyi.skilky.api.UpdateCategoryRequest
import com.vstorchevyi.skilky.data.local.CategoryDao
import com.vstorchevyi.skilky.data.mapper.toDomain
import com.vstorchevyi.skilky.data.mapper.toEntity
import com.vstorchevyi.skilky.data.remote.CategoryApi
import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.CategoryRepository
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * Cache-first wiring of [CategoryRepository]:
 * - `observe()` returns the DAO Flow, so the UI never waits on the network.
 * - `refresh()` GETs the full list and replaces the local cache.
 * - `create/update/delete` write to the server first; the local cache
 *   mirrors the result on success and is left untouched on failure.
 *
 * Every failure collapses into an [AppError] so the domain stays
 * transport-agnostic; the mapping mirrors [AuthRepositoryImpl] for
 * consistency. Transport failures (no connection, DNS, timeout) have no
 * shared supertype across platforms and fall through the catch-all.
 */
internal class CategoryRepositoryImpl(
    private val dao: CategoryDao,
    private val api: CategoryApi,
    private val clock: Clock = Clock.System,
) : CategoryRepository {
    override fun observe(): Flow<List<Category>> = dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refresh(): Either<AppError, Unit> =
        runCatchingApi {
            val now = clock.now().toEpochMilliseconds()
            val entities = api.list().map { it.toEntity(updatedAt = now) }
            dao.clear()
            dao.upsertAll(entities)
            Unit
        }

    override suspend fun create(
        name: String,
        icon: String,
        color: String,
    ): Either<AppError, Category> =
        runCatchingApi {
            val dto = api.create(CreateCategoryRequest(name = name, icon = icon, color = color))
            dao.upsertAll(listOf(dto.toEntity(updatedAt = clock.now().toEpochMilliseconds())))
            dto.toDomain()
        }

    override suspend fun update(
        id: Long,
        name: String,
        icon: String,
        color: String,
    ): Either<AppError, Category> =
        runCatchingApi {
            val dto = api.update(id, UpdateCategoryRequest(name = name, icon = icon, color = color))
            dao.upsertAll(listOf(dto.toEntity(updatedAt = clock.now().toEpochMilliseconds())))
            dto.toDomain()
        }

    override suspend fun delete(id: Long): Either<AppError, Unit> =
        runCatchingApi {
            api.delete(id)
            dao.deleteById(id)
            Unit
        }

    private inline fun <T> runCatchingApi(block: () -> T): Either<AppError, T> =
        try {
            Either.Right(block())
        } catch (e: ResponseException) {
            Either.Left(e.response.status.toAppError())
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            Either.Left(AppError.Network)
        }
}

private fun HttpStatusCode.toAppError(): AppError =
    when {
        this == HttpStatusCode.Unauthorized -> AppError.Unauthorized
        this == HttpStatusCode.Conflict -> AppError.Conflict
        this == HttpStatusCode.UnprocessableEntity -> AppError.Validation
        value >= HTTP_SERVER_ERROR -> AppError.Network
        else -> AppError.Unknown
    }

private const val HTTP_SERVER_ERROR = 500
