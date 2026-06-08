package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.CategoryRepository

/** Remove a user-created category. The server rejects deleting defaults. */
class DeleteCategoryUseCase(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(id: Long): Either<AppError, Unit> = repository.delete(id)
}
