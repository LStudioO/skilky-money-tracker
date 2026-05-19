---
name: kotlin-exposed-patterns
description: Database access patterns for Exposed 1.x in this codebase — DSL queries, the dbQuery suspend bridge, HikariCP config, plain repository classes, Testcontainers integration tests.
---

# Kotlin Exposed Patterns

How the `:server` module talks to Postgres. Uses Exposed 1.x, HikariCP, plain repository classes, and Testcontainers in tests. No DAO entities, no Flyway, no H2.

## When to Use

- Adding a Postgres-backed feature: declare a `Table`, write a repository, test against a real Postgres.
- Editing the `dbQuery` suspend bridge or `DatabaseFactory` lifecycle.
- Writing queries (DSL only — no DAO entities).
- Handling unique-constraint races and converting SQLSTATE errors to typed exceptions.
- Reviewing a PR that touches `server/.../db/` or `server/.../repository/`.

## How It Works

Exposed 1.x uses the `org.jetbrains.exposed.v1.*` namespace. Old snippets pulling from `org.jetbrains.exposed.sql.*` will not compile against the version on the classpath.

JDBC is blocking, Ktor is suspend-based. The `DatabaseFactory.dbQuery { }` helper shifts to `Dispatchers.IO` and opens an Exposed `inTopLevelSuspendTransaction`. Every repository method wraps its work in `dbQuery { }` so the Netty event loop never blocks.

Schema lives in `SchemaUtils.create(...)` at boot. Fine for new tables, but it does not add columns to existing tables — real migrations come once we ship schema changes against prod data.

The repository pattern is one plain class per aggregate, no interface. Tests exercise repositories against a real Postgres via Testcontainers, never mocks. The container is a lazy singleton in `PostgresContainer`; each test starts with a fresh schema via `PostgresContainer.reset()`.

## Examples

### Defining a table

```kotlin
package com.vstorchevyi.skilky.db.tables

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

object UsersTable : LongIdTable("users") {
    val email = varchar("email", length = 255).uniqueIndex()
    val passwordHash = varchar("password_hash", length = 255)
    val displayName = varchar("display_name", length = 100)
    val defaultCurrency = varchar("default_currency", length = 3).default("UAH")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
```

`LongIdTable` gives you an auto-incrementing `id` column. For composite keys, extend plain `Table` and override `primaryKey = PrimaryKey(col1, col2)`.

### A repository

```kotlin
package com.vstorchevyi.skilky.repository

import com.vstorchevyi.skilky.db.DatabaseFactory
import com.vstorchevyi.skilky.db.tables.UsersTable
import com.vstorchevyi.skilky.domain.model.User
import com.vstorchevyi.skilky.errors.ConflictException
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class UserRepository(
    private val databaseFactory: DatabaseFactory,
) {
    suspend fun findByEmail(email: String): User? =
        databaseFactory.dbQuery {
            UsersTable
                .selectAll()
                .where { UsersTable.email eq email }
                .map { it.toUser() }
                .singleOrNull()
        }

    suspend fun create(email: String, passwordHash: String, displayName: String): User =
        databaseFactory.dbQuery {
            try {
                val id = UsersTable.insert {
                    it[UsersTable.email] = email
                    it[UsersTable.passwordHash] = passwordHash
                    it[UsersTable.displayName] = displayName
                }[UsersTable.id].value
                User(id, email, passwordHash, displayName, defaultCurrency = "UAH")
            } catch (e: ExposedSQLException) {
                if (e.sqlState == SQL_STATE_UNIQUE_VIOLATION) {
                    throw ConflictException("Email already registered")
                }
                throw e
            }
        }

    private fun ResultRow.toUser(): User =
        User(
            id = this[UsersTable.id].value,
            email = this[UsersTable.email],
            passwordHash = this[UsersTable.passwordHash],
            displayName = this[UsersTable.displayName],
            defaultCurrency = this[UsersTable.defaultCurrency],
        )

    private companion object {
        const val SQL_STATE_UNIQUE_VIOLATION = "23505"
    }
}
```

Things worth noticing:

- The constructor takes `DatabaseFactory`, not a `Database`. The test fixture swaps in a Testcontainers-backed factory without touching the repository.
- `dbQuery { }` is always the outermost call. Repositories never call Exposed's `transaction` or `newSuspendedTransaction` directly.
- Catch `ExposedSQLException` only when translating a specific SQLSTATE to a typed exception. Branching on `sqlState` (the 5-char SQL-standard code) is portable; don't parse driver-specific message strings.
- Route handlers do not catch exceptions — the `StatusPages` plugin maps `ConflictException` to a 409 with the `ApiErrorResponse` envelope. Let exceptions bubble.

### Translating unique-constraint races

The route layer pre-checks (`findByEmail`) before calling `create`, but the two calls don't share a transaction. Two concurrent registrations with the same email can both pass the pre-check and race into INSERT. The unique index on `email` makes Postgres fail the loser with SQLSTATE `23505`. The repo translates that into a `ConflictException` so the loser sees a clean 409 rather than a 500.

Pattern: pre-check for the common case (UX gives a nice error), trust the DB constraint as the authority.

### Common DSL shapes

```kotlin
// Select + filter
UsersTable.selectAll().where { UsersTable.email eq email }

// Insert returning the generated id
UsersTable.insert { it[email] = ...; it[displayName] = ... }[UsersTable.id].value

// Update
UsersTable.update({ UsersTable.id eq id }) { it[displayName] = newName }

// Delete (lives in org.jetbrains.exposed.v1.jdbc)
UsersTable.deleteWhere { UsersTable.id eq id }

// Aggregate
CategoriesTable.selectAll().where { CategoriesTable.userId eq null }.count()
```

### Joins

```kotlin
suspend fun findExpensesWithCategory(userId: Long): List<ExpenseWithCategory> =
    databaseFactory.dbQuery {
        (ExpensesTable innerJoin CategoriesTable)
            .selectAll()
            .where { ExpensesTable.userId eq userId }
            .orderBy(ExpensesTable.createdAt, SortOrder.DESC)
            .map { row ->
                ExpenseWithCategory(
                    expenseId = row[ExpensesTable.id].value,
                    amount = row[ExpensesTable.amount],
                    categoryName = row[CategoriesTable.name],
                )
            }
    }
```

### Batch insert

```kotlin
ExpensesTable.batchInsert(expenses) { e ->
    this[ExpensesTable.userId] = e.userId
    this[ExpensesTable.amount] = e.amount
    this[ExpensesTable.categoryId] = e.categoryId
}.map { it[ExpensesTable.id].value }
```

## Database setup

`DatabaseFactory` owns the lifecycle. One instance is created in `Application.module()` and passed to every repository.

```kotlin
class DatabaseFactory(
    private val config: AppConfig.DatabaseConfig,
) {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    fun init() {
        dataSource = HikariDataSource(buildHikariConfig())
        database = Database.connect(dataSource)
        transaction(database) {
            SchemaUtils.create(UsersTable, RefreshTokensTable, /* ... */)
        }
    }

    fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            inTopLevelSuspendTransaction(db = database) { block() }
        }

    private fun buildHikariConfig() = HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.user
        password = config.password
        maximumPoolSize = config.maxPoolSize
        driverClassName = "org.postgresql.Driver"
        // Autocommit off so Exposed manages transaction boundaries.
        isAutoCommit = false
        // REPEATABLE READ: stricter than Postgres' default READ COMMITTED.
        // Every SELECT inside one transaction sees the same snapshot.
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        // Warn if a connection is held > 30 s — almost always a leak.
        leakDetectionThreshold = 30_000
        validate()
    }
}
```

Details that matter:

- `isAutoCommit = false`. With autocommit on, every statement becomes an implicit single-statement transaction, defeating the `dbQuery { }` wrapper.
- `TRANSACTION_REPEATABLE_READ` is intentional. Stricter than Postgres' default of `READ COMMITTED`. The cost is occasional serialization failures under heavy write contention — acceptable for our workload.
- `leakDetectionThreshold` catches forgotten connection check-outs without blocking the request.

## Tests

### Repository (Tier 3b)

Integration test against the shared Postgres container. `newRepoFixture()` builds a fresh `DatabaseFactory`, resets the schema, and returns it. Caller closes in `@AfterTest`. JUnit 4 + Kotest assertions + AAA layout.

```kotlin
class UserRepositoryIntegrationTest {
    private lateinit var factory: DatabaseFactory

    @BeforeTest fun setUp() { factory = newRepoFixture() }
    @AfterTest fun tearDown() { factory.close() }

    @Test
    fun `create then findById round-trips the user`() {
        runBlocking {
            // Arrange
            val sut = createSut()

            // Act
            val created = sut.create("vlad@example.com", "hash", "Vlad")
            val found = sut.findById(created.id)

            // Assert
            found shouldBe created
        }
    }

    @Test
    fun `create with duplicate email throws ConflictException`() {
        runBlocking {
            // Arrange
            val sut = createSut()
            sut.create("vlad@example.com", "hash", "Vlad")

            // Act + Assert
            shouldThrow<ConflictException> {
                sut.create("vlad@example.com", "hash", "Vlad")
            }
        }
    }

    private fun createSut() = UserRepository(factory)
}
```

Conventions to follow:

- `runBlocking { ... }` returns the block's result, which makes the JUnit 4 test method's effective return type non-`Unit`. Use a **statement body** (`{ runBlocking { ... } }`), not expression-body `=`. Expression body works for `testApplication { }` because that returns `Unit`.
- The SUT is constructed inside each test via `createSut()`, not as a shared field. The DB factory IS shared in `@BeforeTest` — it's environment, not the SUT.
- `PostgresContainer` is a lazy JVM-wide singleton. Startup (~3 s) is paid once; `reset()` drops/recreates the public schema between tests for isolation.

### Route + DB (Tier 3a)

Use `testApplication { useTestConfigWithDb() }`. `useTestConfigWithDb` seeds the in-memory `MapApplicationConfig` with every required `AppConfig` key plus the Testcontainers DB coordinates. Adding a new required config key requires updating `TestConfig.kt` too — otherwise every Tier 2/3 test fails at boot with `ApplicationConfigurationException`.

### Never mock the database

DB-backed code runs against real Postgres via Testcontainers. Mocks hide migration bugs, SQL bugs, and cascade-delete regressions. The shared container makes the cost acceptable.

## Gradle dependencies

What's already in `server/build.gradle.kts` (versions resolved through `gradle/libs.versions.toml`):

```kotlin
implementation(libs.exposed.core)
implementation(libs.exposed.jdbc)
implementation(libs.exposed.kotlinDatetime)
implementation(libs.hikari)
implementation(libs.postgresql)

testImplementation(libs.testcontainers.postgresql)
testImplementation(libs.testcontainers.core)
testImplementation(libs.kotlin.testJunit)
testImplementation(libs.kotest.assertionsCore)
```

Current pins: Exposed 1.3.0, Hikari 7.0.2, Postgres driver 42.7.11. Bump in lockstep with the upstream Exposed release notes.

What we deliberately do **not** use:

- `exposed-dao` — DSL only, no entity classes.
- Flyway / Liquibase — schema is created via `SchemaUtils.create` at boot. Migrations come later, once there's prod data to migrate. See [Future: migrations with Flyway](#future-migrations-with-flyway) for the planned setup.
- H2 — an in-process Java SQL database (`com.h2database:h2`) commonly used as a fast in-memory test DB. We don't, because H2 has subtle dialect differences from Postgres (JSONB, generated columns, sequence semantics, `ON CONFLICT` quirks) that hide bugs. Testcontainers gives us the real engine for ~3 s of one-time startup, paid once per JVM.

## Future: migrations with Flyway

`SchemaUtils.create(...)` is good enough while we're pre-launch and can wipe data freely. It creates tables that don't exist; it does **not** add columns to existing tables, change types, rename, or backfill. The moment we have prod data to preserve, schema drift becomes a real concern and we switch to versioned migrations.

The plan when that happens:

```kotlin
// gradle/libs.versions.toml
flyway = "11.x.x"
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-postgres = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }

// server/build.gradle.kts
implementation(libs.flyway.core)
implementation(libs.flyway.postgres)
```

```kotlin
// db/Migrations.kt
import org.flywaydb.core.Flyway

fun migrate(config: AppConfig.DatabaseConfig) {
    Flyway.configure()
        .dataSource(config.jdbcUrl, config.user, config.password)
        .locations("classpath:db/migration")
        // Adopt a non-empty existing DB (the SchemaUtils-created one).
        // First Flyway run records the current state as baseline; later
        // versions apply on top. Drop this flag once V1 is established.
        .baselineOnMigrate(true)
        .load()
        .migrate()
}

// Application.kt — call before DatabaseFactory.init()
fun Application.module() {
    val config = AppConfig.from(environment.config)
    migrate(config.database)
    val factory = DatabaseFactory(config.database).also { it.init() }
    // ...
}
```

Migration files live at `server/src/main/resources/db/migration/`, named `V<n>__<description>.sql`. Flyway runs them in version order at boot and records what's been applied in a `flyway_schema_history` table:

```sql
-- server/src/main/resources/db/migration/V1__create_users.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    default_currency VARCHAR(3) NOT NULL DEFAULT 'UAH',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);
```

Migration rules once Flyway is in:

- Never edit a migration that's been applied to any environment. Add a new `V<n+1>__fix_that_thing.sql` instead.
- Keep `SchemaUtils.create` (or remove it) — but the source of truth is the migration history.
- Tests run migrations against the Testcontainers Postgres on container startup, same as prod.

## Quick reference
Le
| Need | Use |
|---|---|
| Run a query | `databaseFactory.dbQuery { ... }` |
| Table with `Long` PK | `object FooTable : LongIdTable("foos")` |
| Insert returning id | `Table.insert { ... }[Table.id].value` |
| Filter | `.selectAll().where { Table.col eq value }` |
| Update rows | `Table.update({ predicate }) { it[col] = newValue }` |
| Delete rows | `Table.deleteWhere { predicate }` |
| Translate unique violation | catch `ExposedSQLException`, check `sqlState == "23505"` |
| Test a repository | `newRepoFixture()` + `runBlocking { ... }` (statement body) |
| Test a route + DB | `testApplication { useTestConfigWithDb() }` |

**Remember:** DSL only, always `dbQuery { }`, real Postgres in tests. The DB is the only authority on uniqueness — pre-checks are UX, constraints are truth.
