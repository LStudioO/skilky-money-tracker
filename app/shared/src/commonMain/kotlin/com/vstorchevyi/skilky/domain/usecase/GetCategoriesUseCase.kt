package com.vstorchevyi.skilky.domain.usecase

import com.vstorchevyi.skilky.domain.model.Category
import com.vstorchevyi.skilky.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

/** Stream the user's categories from the local cache. */
class GetCategoriesUseCase(
    private val repository: CategoryRepository,
) {
    operator fun invoke(): Flow<List<Category>> = repository.getCategories()
}
