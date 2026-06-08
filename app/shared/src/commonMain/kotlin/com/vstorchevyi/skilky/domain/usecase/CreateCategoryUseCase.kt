package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.CategoryRepository

/** Add a user-created category. */
class CreateCategoryUseCase(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(
        name: String,
        icon: String,
        color: String,
    ): Either<AppError, Category> = repository.create(name = name, icon = icon, color = color)
}
