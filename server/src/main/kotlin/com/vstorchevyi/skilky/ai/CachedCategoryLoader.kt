package com.vstorchevyi.skilky.ai

/**
 * In-memory TTL cache for the user-visible category list.
 *
 * Every parse request would otherwise do a DB round-trip for categories
 * that change rarely. Caching per-user for a short window keeps the hot
 * path (text/audio/receipt extraction) off the DB without pulling in a
 * real cache library.
 *
 * **Bounded.** Entries live in an access-ordered LRU capped at [maxUsers],
 * so a long-running server cannot accumulate one entry per user that ever
 * parsed. Eviction drops the least-recently-used user; that user repays a
 * single DB round-trip on their next parse.
 *
 * **Invalidation.** [invalidate] drops a user's entry. The category CRUD
 * routes call it after a create, update, or delete so a changed category
 * shows up as a parse suggestion immediately instead of after the TTL.
 * Without that call staleness is still bounded by [ttlMillis].
 */
class CachedCategoryLoader(
    private val source: suspend (userId: Long) -> List<CategoryHint>,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxUsers: Int = DEFAULT_MAX_USERS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : suspend (Long) -> List<CategoryHint> {
    // Access-ordered so the eldest entry is the least recently used.
    // Guarded by its own monitor; the suspending DB load runs outside the
    // lock, so a cache miss never blocks other users.
    private val entries =
        object : LinkedHashMap<Long, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Entry>): Boolean = size > maxUsers
        }

    override suspend fun invoke(userId: Long): List<CategoryHint> {
        cached(userId)?.let { return it }
        val fresh = source(userId)
        store(userId, fresh)
        return fresh
    }

    /** Drop [userId]'s cached categories so their next parse reloads them. */
    fun invalidate(userId: Long) {
        synchronized(entries) { entries.remove(userId) }
    }

    private fun cached(userId: Long): List<CategoryHint>? =
        synchronized(entries) {
            entries[userId]?.takeIf { nowMillis() - it.loadedAt < ttlMillis }?.value
        }

    private fun store(
        userId: Long,
        value: List<CategoryHint>,
    ) {
        synchronized(entries) { entries[userId] = Entry(value = value, loadedAt = nowMillis()) }
    }

    private data class Entry(
        val value: List<CategoryHint>,
        val loadedAt: Long,
    )

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 60_000
        const val DEFAULT_MAX_USERS: Int = 1_000
        private const val INITIAL_CAPACITY = 16
        private const val LOAD_FACTOR = 0.75f
    }
}
