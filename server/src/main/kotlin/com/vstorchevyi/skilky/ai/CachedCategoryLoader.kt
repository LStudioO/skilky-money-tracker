package com.vstorchevyi.skilky.ai

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory TTL cache for the user-visible category list.
 *
 * Every parse request currently does a DB round-trip for categories
 * that change rarely. Caching per-user for a short window keeps the
 * hot path (text/audio/receipt extraction) off the DB without
 * introducing a real cache library.
 *
 * **Staleness is bounded by [ttlMillis], not invalidated explicitly.**
 * A user who creates a new category and immediately sends a parse
 * request may not see the new category as a suggestion for up to one
 * TTL window. Acceptable for MVP — the user can always pick the
 * category manually in the preview sheet. When the UX feedback says
 * otherwise, hook explicit invalidation into the category CRUD routes.
 */
class CachedCategoryLoader(
    private val source: suspend (userId: Long) -> List<CategoryHint>,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : suspend (Long) -> List<CategoryHint> {
    private val entries = ConcurrentHashMap<Long, Entry>()

    override suspend fun invoke(userId: Long): List<CategoryHint> {
        val now = nowMillis()
        entries[userId]?.takeIf { now - it.loadedAt < ttlMillis }?.let { return it.value }
        val fresh = source(userId)
        entries[userId] = Entry(value = fresh, loadedAt = now)
        return fresh
    }

    private data class Entry(
        val value: List<CategoryHint>,
        val loadedAt: Long,
    )

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 60_000
    }
}
