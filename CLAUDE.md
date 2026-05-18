# Skilky

A budget tracker. KMP client (Android + iOS) plus a Ktor backend. Self-hosted AI parses text, voice, and receipt photos into structured expenses.

## Source-of-truth docs

Big-picture decisions, schemas, and the roadmap live in [`docs/`](docs/). Look here first:

- [`docs/overview.md`](docs/overview.md): vision and how the app works
- [`docs/architecture.md`](docs/architecture.md): module graph, data flows, auth flow
- [`docs/tech-stack.md`](docs/tech-stack.md): what we picked and why
- [`docs/api-spec.md`](docs/api-spec.md): REST endpoints, error envelope
- [`docs/data-models.md`](docs/data-models.md): DB schemas, DTOs
- [`docs/implementation-phases.md`](docs/implementation-phases.md): phased roadmap with checkpoints

## Module map

Follows the [recommended KMP structure](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html) for projects with a server: `core` + `server` at the repo root, client code under `app/`.

| Module | What it is |
|---|---|
| `:core` | KMP library: API contract (DTOs, routes, validation). Used by `:server` and `:app:shared`. |
| `:server` | Ktor backend, JVM only. See [`server/CLAUDE.md`](server/CLAUDE.md). |
| `:app:shared` | Compose Multiplatform UI + iOS framework. Most client code lives here. |
| `:app:androidApp` | Android application entry point. |
| `:app:desktopApp` | Desktop entry point; use for Compose Hot Reload while editing shared UI. |
| `app/iosApp/` | Xcode host for iOS (not a Gradle module). |
| `:build-logic` | Convention plugins (`skilky.kotlin-jvm`, `skilky.kotlin-multiplatform`, `skilky.android-app`, `skilky.detekt`, `skilky.spotless`). |

## Working in this repo

- JDK 21 toolchain (configured via `skilky.kotlin-jvm` convention plugin).
- Lint and tests run on every PR via `.github/workflows/build.yml`. Format violations or test failures block merge.
- Before committing Kotlin changes: `./gradlew spotlessApply`.

## Version-lookup workflow

When checking "what's the latest version of X":

1. Hit Maven Central directly: `https://repo1.maven.org/maven2/<group-path>/<artifact>/maven-metadata.xml` and read the last `<version>` entries.
2. For Gradle plugins, also check the portal: `https://plugins.gradle.org/m2/<group-path>/<artifact>/`.

Do not trust `search.maven.org`'s solr index. It lags weeks to months behind the actual repo metadata.
