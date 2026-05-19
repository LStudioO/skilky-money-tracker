package com.vstorchevyi.skilky.ai

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test

class CachedCategoryLoaderTest {
    @Test
    fun `repeated reads inside the TTL hit the cache and skip the source`() {
        val calls = AtomicInteger(0)
        val sut =
            createSut(
                source = {
                    calls.incrementAndGet()
                    listOf(CategoryHint(id = 1, name = "Food"))
                },
            )

        runBlocking {
            sut(USER_ID)
            sut(USER_ID)
            sut(USER_ID)
        }

        withClue("three reads inside the TTL should call the source exactly once") {
            calls.get() shouldBe 1
        }
    }

    @Test
    fun `read after TTL refetches`() {
        val calls = AtomicInteger(0)
        var clock = 0L
        val sut =
            createSut(
                source = {
                    calls.incrementAndGet()
                    listOf(CategoryHint(id = 1, name = "Food"))
                },
                nowMillis = { clock },
                ttlMillis = 60_000,
            )

        runBlocking {
            sut(USER_ID)
            clock = 30_000
            sut(USER_ID)
            clock = 61_000
            sut(USER_ID)
        }

        calls.get() shouldBe 2
    }

    @Test
    fun `two users have independent buckets`() {
        val calls = AtomicInteger(0)
        val sut =
            createSut(
                source = { userId ->
                    calls.incrementAndGet()
                    listOf(CategoryHint(id = userId, name = "U$userId"))
                },
            )

        val (a, b) =
            runBlocking {
                sut(1L) to sut(2L)
            }

        calls.get() shouldBe 2
        a.single().id shouldBe 1L
        b.single().id shouldBe 2L
    }

    private fun createSut(
        source: suspend (Long) -> List<CategoryHint>,
        nowMillis: () -> Long = { 0L },
        ttlMillis: Long = 60_000,
    ) = CachedCategoryLoader(source = source, ttlMillis = ttlMillis, nowMillis = nowMillis)

    companion object {
        private const val USER_ID = 42L
    }
}
