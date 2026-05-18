# Server tests

Conventions for everything under `server/src/test/`.

## Stack

- `kotlin-test` + JUnit 4 runner (via `kotlin-test-junit`).
- **Kotest assertions** for matchers. The dependency we use today is `io.kotest:kotest-assertions-core-jvm` (JVM-only target). Kotest publishes a multiplatform marker artifact (`io.kotest:kotest-assertions-core`, without the `-jvm` suffix) which is what `:shared/commonTest` would consume once validation tests move there. The matcher API is the same; only the coordinate differs.
- Ktor test host (`testApplication { }`) for route tests.
- Testcontainers Postgres for integration tests.

## Three test tiers

| Tier | What it needs | Examples |
|---|---|---|
| 1. Pure unit | No Ktor, no DB | `ValidatorsTest`, `PasswordHasherTest`, `JwtTokenProviderTest`, `TokenHasherTest` |
| 2. Route | `testApplication` + `useTestConfig()` | `HealthRoutesTest`, `StatusPagesTest`, `CallIdTest` |
| 3a. Route + DB | `testApplication` + `useTestConfigWithDb()`, real Postgres | `AuthRoutesIntegrationTest` |
| 3b. Repository | `newRepoFixture()` + `runBlocking`, no Ktor, real Postgres | `UserRepositoryIntegrationTest`, `RefreshTokenRepositoryIntegrationTest` |

Tests live in a package mirroring the code under test.

## Conventions

### AAA (Arrange / Act / Assert)

Each test has three labelled sections separated by blank lines:

```kotlin
@Test
fun `verify rejects a different password`() {
    // Arrange
    val sut = createSut()
    val hash = sut.hash("secret123")

    // Act
    val verified = sut.verify("wrong-password", hash)

    // Assert
    verified shouldBe false
}
```

### SUT construction

The SUT is constructed inside each test's Arrange section via a `createSut()` factory that lives at the bottom of the class. **No shared `sut` field.** Resource fixtures (DB factory, Postgres container) are allowed in `@BeforeTest`; they are the environment, not the SUT.

```kotlin
class FooTest {
    @Test
    fun `does the thing`() {
        // Arrange
        val sut = createSut()
        // ...
    }

    // Helpers at the bottom
    private fun createSut(cost: Int = 4) = Foo(cost)
}
```

### Function builders

Test data builders use `aFoo(...)` naming. Shared builders live in `support/Builders.kt`. Each builder provides sensible defaults so call sites name only the parameter the test cares about:

```kotlin
val request = aRegisterRequest(email = "not-an-email")
```

Class-local builders (`createSut`, `givenUser`, `backdateTokensFor`, ...) belong at the bottom of the class, below the tests.

### Coroutines and JUnit 4

JUnit 4 test methods must have an effective return type of `Unit`. The constraint is on the **method's return type**, not the syntactic body form.

Expression-body `=` works when the right-hand side returns `Unit`. `testApplication { ... }` returns `Unit`, so route tests use:

```kotlin
@Test
fun `register issues 201`() =
    testApplication {
        // ...
    }
```

Expression-body fails when the right-hand side returns something else. `runBlocking { repo.findValid(token) }` returns `RefreshTokenRecord?`, which makes the test method's return type non-`Unit` and JUnit 4 rejects it with `InvalidTestClassError`. For repository tests, use a statement body:

```kotlin
@Test
fun `findValid returns null for an expired token`() {
    runBlocking {
        // body returns RefreshTokenRecord?, but the test method returns Unit
    }
}
```

`runBlocking` lives in `kotlinx-coroutines-core` (already on the classpath). No need for `kotlinx-coroutines-test`; we are not testing time-based concurrency.

### Assertions

Kotest matchers everywhere. Failure messages are auto-generated and rich. Only add a `withClue("...")` wrapper when the business intent of an assertion would not be obvious from the assertion expression alone:

```kotlin
withClue("the loser should see a clean 409, not a 500 from the DB constraint") {
    conflicts shouldBe 1
}
```

Patterns worth noting:
- `nullable.shouldNotBeNull()` and `nullable.shouldBeNull()` for null checks, rather than `assertNotNull`.
- `shouldNotBeBlank()` for tokens and strings that must be present.
- `shouldMatch Regex(...)` for output format checks (hex, UUID, etc.).
- `shouldThrow<T> { ... }` and `shouldNotThrow<T> { ... }` for exception assertions.

### DB test isolation

`PostgresContainer.reset()` in `@BeforeTest` drops and recreates the schema so each test starts against an empty DB. The container itself is a lazy singleton shared across the whole test JVM (~3 s startup is paid once).

### Never mock the database

DB-backed code is exercised against a real Postgres via Testcontainers. Mocks hide migration bugs, SQL bugs, and cascade-delete regressions. The shared container makes the cost acceptable.

### Same envelope for security paths

For paths that should resist enumeration (login wrong-password vs unknown-user, refresh expired vs invalid), assert that both paths produce the **same** envelope (status + code), not just the same HTTP status. Drift in either field gives attackers a side channel.

## Test support inventory

All under `server/src/test/kotlin/com/vstorchevyi/skilky/support/`.

| File | Purpose |
|---|---|
| `TestConfig.kt` | `useTestConfig()` seeds the in-memory `MapApplicationConfig` with every required `AppConfig` key. **Adding a new required config key requires adding it here too**, otherwise every Tier 2/3 test fails at boot with `ApplicationConfigurationException`. |
| `PostgresContainer.kt` | Shared Postgres container + `useTestConfigWithDb()` config helper. `reset()` drops/recreates the public schema between tests. |
| `JsonClient.kt` | `jsonClient()` builds a Ktor client with the same JSON config the server uses. |
| `Builders.kt` | `aRegisterRequest()`, `aLoginRequest()`, `aRefreshRequest()`, `aUser()`, `aJwtConfig()`. |
| `RepoFixture.kt` | `newRepoFixture()` resets the schema and returns a fresh `DatabaseFactory` pointing at the shared container. Caller closes it in `@AfterTest`. |

## When to add a new helper

A helper in `support/` earns its place when two or more tests would otherwise duplicate the same setup. One-off setup belongs inline so the test reads as a single artifact.
