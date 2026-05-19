---
name: gen-test
description: Generate tests for a Kotlin class following Skilky conventions. Use when the user asks to write tests, add coverage, or says "test this", "cover this", "write tests for X". Also activates when the user names a specific route, repository, validator, hasher, plugin, DTO, or use case and wants it tested. Picks the right tier (pure unit, route, route+DB, repository) and matches the existing kotlin.test + Kotest + Testcontainers + Ktor testApplication style.
---

Generate tests for: $ARGUMENTS

## Context

Two test trees in this repo.

- `server/src/test/kotlin/...` is JVM. Uses JUnit 4 runner, Kotest assertions, real Postgres via Testcontainers, Ktor `testApplication { }`. Full conventions in `server/src/test/CLAUDE.md`.
- `core/src/commonTest/kotlin/...` and `app/shared/src/commonTest/kotlin/...` are KMP. `kotlin.test` assertions only. Kotest's `-jvm` artifact does not work in `commonTest` yet, see the note in `server/src/test/CLAUDE.md`.

No mocking framework is on the classpath. No Mockk, no Mokkery, no assertk. Don't add one. DB-backed code runs against real Postgres. Pure code runs without fakes.

## Pick the right tier (server)

| Tier | What it needs | Example in repo |
|---|---|---|
| 1. Pure unit | No Ktor, no DB | `server/src/test/.../security/ValidatorsTest.kt`, `PasswordHasherTest.kt`, `JwtTokenProviderTest.kt` |
| 2. Route | `testApplication { useTestConfig() }` | `server/src/test/.../plugins/StatusPagesTest.kt`, `CallIdTest.kt` |
| 3a. Route + DB | `testApplication { useTestConfigWithDb() }`, real Postgres | route integration tests as they land |
| 3b. Repository | `newRepoFixture()` + `runBlocking`, no Ktor, real Postgres | `server/src/test/.../repository/UserRepositoryIntegrationTest.kt`, `RefreshTokenRepositoryIntegrationTest.kt` |

For KMP code in `:core` or `:app:shared` (DTOs, pure helpers):

- `assertEquals`, `assertNull`, `assertTrue` from `kotlin.test`. No Kotest.
- See `core/src/commonTest/.../api/AuthDtosTest.kt` for the style.
- Shared builders live in `core/src/commonTest/.../support/TestBuilders.kt`.

## Process

1. Read the source file to understand what to cover.
2. Pick the tier from the table above.
3. Check for an existing builder. Server builders are in `server/src/test/.../support/Builders.kt` (`aRegisterRequest`, `aLoginRequest`, `aRefreshRequest`, `aUser`, `aJwtConfig`). KMP builders are in `core/src/commonTest/.../support/TestBuilders.kt`. Reuse them. Add a new `aFoo(...)` only when at least two tests would duplicate the same setup.
4. Mirror the source package under the matching test root.
5. Cover happy path, error path, and edge cases (null, empty, boundary).
6. Run the focused test:
   - Server: `./gradlew :server:test --tests "*ClassName*"`
   - KMP: `./gradlew :core:allTests --tests "*ClassName*"` or `:app:shared:allTests`
7. Verify all pass.

## Conventions

Lifted from `server/src/test/CLAUDE.md`. Short version follows.

**Naming.** Backticks: `` `should X when Y` ``, or just `` `does the thing` `` when context is obvious.

**AAA blocks.** `// Arrange`, `// Act`, `// Assert` comments separated by blank lines. Single-line act-plus-assert is fine when there is nothing to arrange.

**SUT construction.** Build the SUT inside each test through a `createSut(...)` factory at the bottom of the class. No shared `sut` field. Resource fixtures like the DB factory go in `@BeforeTest`, the SUT does not.

```kotlin
class FooTest {
    @Test
    fun `does the thing`() {
        val sut = createSut()
        sut.act() shouldBe expected
    }

    private fun createSut(cost: Int = 4) = Foo(cost)
}
```

**Builders.** `aFoo(...)` naming, sensible defaults, call sites name only the parameter the test cares about: `aRegisterRequest(email = "not-an-email")`.

**JUnit 4 plus coroutines.** JUnit 4 test methods need a `Unit` return type. `testApplication { ... }` returns `Unit` so expression body works:

```kotlin
@Test
fun `register issues 201`() = testApplication { ... }
```

`runBlocking { repo.findValid(token) }` returns a value, which breaks expression body. Use a statement body for repository tests:

```kotlin
@Test
fun `findValid returns null for an expired token`() {
    runBlocking { ... }
}
```

**Assertions.**
- Server uses Kotest matchers: `shouldBe`, `shouldNotBe`, `shouldBeNull`, `shouldNotBeNull`, `shouldNotBeBlank`, `shouldMatch Regex(...)`, `shouldThrow<T> { ... }`, `shouldNotThrow<T> { ... }`.
- KMP common uses `kotlin.test`: `assertEquals`, `assertNull`, `assertTrue`, `assertFailsWith`.
- Add `withClue("...")` only when the intent of an assertion is not obvious from the expression.

**DB isolation.** `PostgresContainer.reset()` in `@BeforeTest` drops and recreates the public schema. The container is a lazy singleton shared across the JVM. Roughly 3s startup paid once.

**Never mock the database.** Mocks hide migration bugs, SQL bugs, and cascade-delete regressions. Use Testcontainers.

**Same envelope for security paths.** When testing login wrong-password vs unknown-user, or refresh expired vs invalid, assert the same status plus the same error code. Drift gives attackers a side channel.

**One assertion per test**, or one logical group.

**No real I/O** outside the Postgres container.

## Adding a helper to `support/`

A helper earns its place when two or more tests would otherwise duplicate the same setup. One-off setup belongs inline so the test reads as a single artifact. When adding a new required `AppConfig` key, update `support/TestConfig.kt` too. Tier 2 and Tier 3 tests boot through it and will throw `ApplicationConfigurationException` at startup if a key is missing.
