package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.AppError
import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.model.Either
import com.vstorchevyi.skilky.domain.repository.CategoryRepository

/** Rename or recolor an existing user-created category. */
class UpdateCategoryUseCase(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        icon: String,
        color: String,
    ): Either<AppError, Category> = repository.update(id = id, name = name, icon = icon, color = color)
}
