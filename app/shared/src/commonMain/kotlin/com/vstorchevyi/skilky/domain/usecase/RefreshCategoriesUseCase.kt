package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.CategoryRepository

/** Pull the latest list from the server into the local cache. */
class RefreshCategoriesUseCase(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(): Either<AppError, Unit> = repository.refresh()
}
