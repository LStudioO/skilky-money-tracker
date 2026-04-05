# Tech Stack

## Client

| Layer | Technology | Why |
|-------|-----------|-----|
| UI Framework | Compose Multiplatform | Single codebase for Android + iOS, Kotlin-native |
| Platforms | Android + iOS | Primary targets. Wear OS future. |
| Architecture | MVI (Model-View-Intent) | Unidirectional data flow, predictable state, testable logic |
| Navigation | Navigation Compose (JetBrains KMP port) | Same API as Android, type-safe @Serializable routes, official support |
| DI | Koin | KMP-native, first-class Compose integration, simple |
| Local DB | Room KMP | Familiar to Android devs, stable KMP support |
| Networking | Ktor Client (OkHttp / Darwin) | KMP-native; OkHttp engine on Android, Darwin (URLSession) on iOS |
| Serialization | kotlinx-serialization | Kotlin-native, used by both Ktor client and server |
| Image Loading | Coil | Compose Multiplatform support |
| Date/Time | kotlinx-datetime | KMP-native, no java.time dependency on iOS |
| Localization | Compose MP Resources | Official JetBrains solution, compile-time safe, familiar XML format |

## Server

| Layer | Technology | Why |
|-------|-----------|-----|
| Framework | Ktor (Netty engine) | Kotlin-native, lightweight, coroutine-based |
| ORM | Exposed | JetBrains Kotlin ORM, DSL feels natural for Kotlin devs |
| Database | PostgreSQL | Production-ready, Docker-friendly, great tooling |
| Auth | JWT (ktor-server-auth-jwt) | Stateless, multi-device friendly |
| Password Hashing | BCrypt | Industry standard |
| DI | Koin (ktor integration) | Same DI framework as client |

## AI / ML

| Task | Technology | Model | Notes |
|------|-----------|-------|-------|
| Text Parsing | Ollama | llama3.2 / mistral | Multilingual — handles Ukrainian + English |
| Receipt Vision | Ollama | LLaVA or Moondream | Multimodal — reads receipt images directly |
| Speech-to-Text | Speaches (faster-whisper) | faster-whisper-small/medium | Ukrainian + English supported in medium/large models |

### Why Ollama?

- Runs as a single Docker container
- Simple REST API (`/api/chat`)
- Supports text LLMs and vision models
- Users can swap models easily
- Huge community, active development
- No vendor lock-in

### Why not Google AI Studio / cloud models?

- Contradicts the self-hosting / no proprietary dependency goal
- Free tier can change anytime
- Self-hosted users would need their own API keys
- **Future:** optional cloud AI adapter (Gemini, OpenAI-compatible) for users who prefer it

## DevOps

| Tool | Purpose |
|------|---------|
| Docker Compose | Local + production deployment — one command to run everything |
| GitHub Actions | CI/CD — build, test, Docker image publish |
| GHCR | Docker image registry (GitHub Container Registry) |

## Static Analysis

| Tool | Purpose | Scope |
|------|---------|-------|
| **Detekt** | Static analysis — code smells, complexity, custom rules | All Kotlin modules |
| **Ktlint** (via Spotless) | Code formatting — consistent style | All Kotlin modules |

- Both configured in `build-logic` convention plugins — applied to every module automatically
- Detekt config: `config/detekt/detekt.yml`
- Formatting rules: `.editorconfig` at repo root
- CI: lint job runs before build — PRs with failures won't pass

## Testing

| Layer | Technology | Why |
|-------|-----------|-----|
| Unit Tests | kotlin.test | KMP-native, works in commonTest across all targets |
| Mocking | MockK | Kotlin-first mocking, supports coroutines and suspend functions |
| Flow Testing | Turbine | Concise Flow/StateFlow assertions with `test {}` block |
| Coroutine Tests | kotlinx-coroutines-test | `runTest`, `TestDispatcher`, `advanceUntilIdle()` |
| UI Tests | Compose UI Test | `createComposeRule()`, semantic matchers, KMP support |

## Build System

| Tool |
|------|
| Kotlin |
| Gradle |
| AGP |
| KSP |

## Key Architectural Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Client architecture | MVI | Unidirectional data flow, predictable state, debuggable |
| Navigation | Navigation Compose (JetBrains KMP) | Familiar Android API, official support, type-safe routes |
| Client DI | Koin | KMP-native, simple, first-class Compose support |
| Client DB | Room KMP | Familiar to Android devs, stable KMP since 2.8 |
| Server DB | Exposed + PostgreSQL | Kotlin DSL ORM by JetBrains + production-ready DB |
| AI hosting | Ollama + Speaches (Docker) | Self-hosted, swappable models, no vendor lock-in |
| Offline strategy | Local queue + sync | Avoids shipping GB-sized AI models to device |
| Auth | JWT + refresh tokens | Stateless, multi-device, works for self-hosted |
| Shared code | shared:models + shared:core modules | API contracts never drift, business logic reused |
| Deployment | Docker Compose | One command, everything runs |
