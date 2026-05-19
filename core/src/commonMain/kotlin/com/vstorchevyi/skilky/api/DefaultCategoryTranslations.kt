package com.vstorchevyi.skilky.api

/**
 * Localized display names for [DefaultCategoryKeys]. Used by the server for
 * `GET /categories` and nested category fields when `name_key` is set.
 *
 * [languageTag] is typically a normalized primary subtag from
 * `Accept-Language` (e.g. `en`, `uk`). Unknown languages fall back to English.
 *
 * **Why in code (for now):** a handful of fixed strings per locale stays simple for a solo
 * backend and avoids an extra DB or CMS dependency. If translators or many locales join the
 * project, move strings to classpath resources or a TMS and keep the lookup API stable.
 */
object DefaultCategoryTranslations {
    private val byKey: Map<String, Map<String, String>> =
        mapOf(
            DefaultCategoryKeys.FOOD to
                mapOf(
                    "en" to "Food",
                    "uk" to "Їжа",
                ),
            DefaultCategoryKeys.TRANSPORT to
                mapOf(
                    "en" to "Transport",
                    "uk" to "Транспорт",
                ),
            DefaultCategoryKeys.HOUSING to
                mapOf(
                    "en" to "Housing",
                    "uk" to "Житло",
                ),
            DefaultCategoryKeys.ENTERTAINMENT to
                mapOf(
                    "en" to "Entertainment",
                    "uk" to "Розваги",
                ),
            DefaultCategoryKeys.HEALTH to
                mapOf(
                    "en" to "Health",
                    "uk" to "Здоров'я",
                ),
            DefaultCategoryKeys.SHOPPING to
                mapOf(
                    "en" to "Shopping",
                    "uk" to "Покупки",
                ),
            DefaultCategoryKeys.BILLS to
                mapOf(
                    "en" to "Bills",
                    "uk" to "Рахунки",
                ),
            DefaultCategoryKeys.EDUCATION to
                mapOf(
                    "en" to "Education",
                    "uk" to "Освіта",
                ),
            DefaultCategoryKeys.OTHER to
                mapOf(
                    "en" to "Other",
                    "uk" to "Інше",
                ),
        )

    fun normalizeLanguageTag(raw: String?): String {
        if (raw.isNullOrBlank()) return "en"
        val primary =
            raw
                .split(',')
                .firstOrNull()
                ?.trim()
                ?.substringBefore(';')
                ?.trim()
                ?.substringBefore('-')
                ?.lowercase()
                .orEmpty()
        return when (primary) {
            "uk" -> "uk"
            else -> "en"
        }
    }

    fun displayName(
        nameKey: String?,
        storedName: String,
        languageTag: String,
    ): String {
        if (nameKey == null) return storedName
        val lang = normalizeLanguageTag(languageTag)
        val perKey = byKey[nameKey] ?: return storedName
        return perKey[lang] ?: perKey["en"] ?: storedName
    }

    /**
     * English translation for [nameKey], or `null` if the key is absent.
     * Used by server seeding to enforce that every default key carries an
     * English string at boot.
     */
    fun englishOrNull(nameKey: String): String? = byKey[nameKey]?.get("en")
}
