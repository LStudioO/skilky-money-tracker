package com.vstorchevyi.skilky.domain.repository

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Test double for [CategoryRepository]. Each method records its call on
 * [calls]; tests queue replies via the `*Result` setters. The `getCategories()`
 * stream is a `MutableStateFlow` so a test can push fresh values to simulate
 * incoming DAO updates.
 */
class FakeCategoryRepository(
    initial: List<Category> = emptyList(),
) : CategoryRepository {
    val calls: MutableList<Call> = mutableListOf()

    private val categories = MutableStateFlow(initial)

    var refreshResult: Either<AppError, Unit> = Either.Right(Unit)
    var createResult: Either<AppError, Category> =
        Either.Right(Category(0, "", "", "", isDefault = false))
    var updateResult: Either<AppError, Category> = createResult
    var deleteResult: Either<AppError, Unit> = Either.Right(Unit)

    override fun getCategories(): Flow<List<Category>> = categories.asStateFlow()

    override suspend fun refresh(): Either<AppError, Unit> {
        calls += Call.Refresh
        return refreshResult
    }

    override suspend fun create(
        name: String,
        icon: String,
        color: String,
    ): Either<AppError, Category> {
        calls += Call.Create(name, icon, color)
        if (createResult is Either.Right) {
            val created = (createResult as Either.Right<Category>).value
            categories.update { it + created }
        }
        return createResult
    }

    override suspend fun update(
        id: Long,
        name: String,
        icon: String,
        color: String,
    ): Either<AppError, Category> {
        calls += Call.Update(id, name, icon, color)
        if (updateResult is Either.Right) {
            val updated = (updateResult as Either.Right<Category>).value
            categories.update { list -> list.map { if (it.id == id) updated else it } }
        }
        return updateResult
    }

    override suspend fun delete(id: Long): Either<AppError, Unit> {
        calls += Call.Delete(id)
        if (deleteResult is Either.Right) {
            categories.update { list -> list.filterNot { it.id == id } }
        }
        return deleteResult
    }

    fun setCategories(value: List<Category>) {
        categories.value = value
    }

    sealed interface Call {
        data object Refresh : Call

        data class Create(
            val name: String,
            val icon: String,
            val color: String,
        ) : Call

        data class Update(
            val id: Long,
            val name: String,
            val icon: String,
            val color: String,
        ) : Call

        data class Delete(
            val id: Long,
        ) : Call
    }
}
