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

| Module | What it is |
|---|---|
| `:server` | Ktor backend, JVM only. See [`server/CLAUDE.md`](server/CLAUDE.md) for conventions. |
| `:shared` | KMP module holding the API contract: DTOs, route constants, shared validation. Imported by both `:server` and `:composeApp`. Pure Kotlin in `commonMain`. Uses `kotlinx-serialization` and `kotlinx-datetime` only. |
| `:composeApp` | Compose Multiplatform UI library. Targets Android, iOS, JVM desktop. Most app code lives here. |
| `:androidApp` | Thin Android app entry point. Hosts `App()` from `:composeApp`. Almost no logic. |
| `:build-logic` | Class-based Gradle convention plugins (`skilky.kotlin-jvm`, `skilky.kotlin-multiplatform`, `skilky.android-app`, `skilky.detekt`, `skilky.spotless`). Plugins live under `com.vstorchevyi.skilky.gradle`. |

## Working in this repo

- JDK 21 toolchain (configured via `skilky.kotlin-jvm` convention plugin).
- Lint and tests run on every PR via `.github/workflows/build.yml`. Format violations or test failures block merge.
- Before committing Kotlin changes: `./gradlew spotlessApply`.

## Version-lookup workflow

When checking "what's the latest version of X":

1. Hit Maven Central directly: `https://repo1.maven.org/maven2/<group-path>/<artifact>/maven-metadata.xml` and read the last `<version>` entries.
2. For Gradle plugins, also check the portal: `https://plugins.gradle.org/m2/<group-path>/<artifact>/`.

Do not trust `search.maven.org`'s solr index. It lags weeks to months behind the actual repo metadata.
