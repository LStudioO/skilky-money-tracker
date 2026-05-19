# :server module

JVM-only Ktor 3.x server. Uses PostgreSQL with Exposed 1.x ORM and HikariCP.

## File organization

```
src/main/kotlin/com/vstorchevyi/skilky/
├── Application.kt                 # Application.module(): installs plugins, registers routes
├── ai/                            # Ollama HTTP client, prompt templates, parsing orchestration
├── config/                        # Typed config wrappers over HOCON
├── plugins/                       # One file per Ktor plugin install (Serialization, StatusPages, etc.)
├── routes/                        # One file per feature group: fun Route.xxxRoutes(...)
├── repository/                    # Suspend functions wrapping dbQuery { }
└── db/
    └── tables/                    # Exposed Table objects
```

DTOs do not live here. They live in `:core/.../api/` so the client can import them. Server-only response shapes are a rare exception.

## Exposed 1.x conventions

**Use `org.jetbrains.exposed.v1.*` package paths.** The 1.0 release added a `v1` namespace. Old `org.jetbrains.exposed.sql.*` imports are wrong:

```kotlin
// correct
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelSuspendTransaction

// wrong (will not compile)
import org.jetbrains.exposed.sql.Table
```

**`dbQuery { }` is our suspend bridge.** JDBC is blocking. We move it to `Dispatchers.IO` and use the official suspend transaction:

```kotlin
suspend fun <T> dbQuery(block: suspend () -> T): T = withContext(Dispatchers.IO) {
    inTopLevelSuspendTransaction { block() }
}
```

Reference: [github.com/JetBrains/Exposed/tree/main/samples/exposed-ktor](https://github.com/JetBrains/Exposed/tree/main/samples/exposed-ktor). Copy import lines, transaction shape, and column types from here when unsure.

## Patterns

- Routes: `fun Route.xxxRoutes(deps)` extension on `Route`, registered in `plugins/Routing.kt`.
- Plugins: `fun Application.configureX(...)` extension on `Application`. Each installs one Ktor plugin. Wired in `Application.module()`.
- Repositories: plain classes with `suspend` functions. Each function wraps its work in `dbQuery { }`. No DAOs.
- Errors: route handlers throw, `StatusPages` plugin maps them to the `ApiErrorResponse` envelope. Don't `try/catch` for HTTP status codes. Let it bubble.

## Config

- HOCON at `src/main/resources/application.conf`. Secrets via `${?ENV_VAR}` substitution, never hardcoded.
- Read into a typed `AppConfig` data class once at `Application.module()` start. Pass `AppConfig` (or its subtypes) into route, plugin, and repository constructors.

## Tests

- `kotlin.test` plus `io.ktor.server.testing.testApplication { }`.
- `testApplication` builds an empty `MapApplicationConfig` by default. Anything that reads `environment.config` must seed it:
  ```kotlin
  environment { config = MapApplicationConfig("skilky.api.version" to "1.0.0", ...) }
  application { module() }
  ```
- DB-backed tests should use Testcontainers (postgres). Never run them against a real shared DB.
