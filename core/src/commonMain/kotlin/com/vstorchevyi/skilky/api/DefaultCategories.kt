package com.vstorchevyi.skilky.api

/**
 * System default categories seeded once in Postgres (`user_id` is null).
 * Each row stores [Template.key] in `name_key` and English in `name` as a
 * fallback; API responses localize via [DefaultCategoryTranslations].
 *
 * Icons use the `material:<name>` prefix from [docs/data-models.md].
 */
object DefaultCategories {
    data class Template(
        val key: String,
        val icon: String,
        val color: String,
    )

    val ALL: List<Template> =
        listOf(
            Template(DefaultCategoryKeys.FOOD, "material:restaurant", "#4CAF50"),
            Template(DefaultCategoryKeys.TRANSPORT, "material:directions_car", "#2196F3"),
            Template(DefaultCategoryKeys.HOUSING, "material:home", "#9C27B0"),
            Template(DefaultCategoryKeys.ENTERTAINMENT, "material:movie", "#E91E63"),
            Template(DefaultCategoryKeys.HEALTH, "material:medical_services", "#F44336"),
            Template(DefaultCategoryKeys.SHOPPING, "material:shopping_bag", "#FF9800"),
            Template(DefaultCategoryKeys.BILLS, "material:receipt_long", "#607D8B"),
            Template(DefaultCategoryKeys.EDUCATION, "material:school", "#3F51B5"),
            Template(DefaultCategoryKeys.OTHER, "material:more_horiz", "#795548"),
        )
}
