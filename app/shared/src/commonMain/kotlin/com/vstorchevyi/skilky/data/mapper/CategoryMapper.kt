package com.vstorchevyi.skilky.data.mapper

import com.vstorchevyi.skilky.api.CategoryDto
import com.vstorchevyi.skilky.data.local.CategoryEntity
import com.vstorchevyi.skilky.domain.model.Category

/**
 * Turns the `:core` wire DTO into the on-disk entity. `updatedAt` is set by
 * the caller (typically `Clock.System.now().toEpochMilliseconds()` at the
 * write site) so the entity carries a write timestamp without the mapper
 * needing access to a clock.
 */
internal fun CategoryDto.toEntity(updatedAt: Long): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isDefault = isDefault,
        nameKey = nameKey,
        updatedAt = updatedAt,
    )

internal fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isDefault = isDefault,
        nameKey = nameKey,
    )

internal fun CategoryDto.toDomain(): Category =
    Category(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isDefault = isDefault,
        nameKey = nameKey,
    )
