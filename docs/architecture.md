# Architecture

## System Architecture

```mermaid
graph TB
    subgraph Clients
        A[Android App]
        I[iOS App]
    end

    subgraph Docker
        subgraph Backend
            K[Ktor Server]
        end
        subgraph Database
            P[(PostgreSQL)]
        end
        subgraph AI
            O[Ollama gemma4:e4b]
        end
    end

    A -- HTTPS --> K
    I -- HTTPS --> K
    K -- JDBC --> P
    K -- HTTP --> O

    style A fill:#4CAF50,color:#fff
    style I fill:#2196F3,color:#fff
    style K fill:#FF9800,color:#fff
    style P fill:#9C27B0,color:#fff
    style O fill:#F44336,color:#fff
```



## Monorepo Module Structure

```
skilky-money-tracker/
├── build-logic/                    # Convention plugins (composite build)
├── core/                           # :core — DTOs, enums, API routes, validation (client + server)
├── server/                         # :server — Ktor backend
├── app/
│   ├── shared/                     # :app:shared — CMP UI (Android library + iOS framework)
│   ├── androidApp/                 # :app:androidApp — Android application
│   ├── desktopApp/                 # :app:desktopApp — Desktop (Hot Reload sandbox)
│   └── iosApp/                     # Xcode iOS host (not a Gradle module)
├── docker/                         # Docker Compose + .env
├── gradle/libs.versions.toml       # Version catalog
├── .github/workflows/              # CI/CD
└── settings.gradle.kts
```

### Module Dependency Graph

```mermaid
graph LR
    AS[app:shared]
    AA[app:androidApp]
    AD[app:desktopApp]
    S[server]
    C[core]

    AA --> AS
    AD --> AS
    AS --> C
    S --> C

    style AS fill:#4CAF50,color:#fff
    style AA fill:#4CAF50,color:#fff
    style AD fill:#4CAF50,color:#fff
    style S fill:#FF9800,color:#fff
    style C fill:#9C27B0,color:#fff
```



### Module details

#### `:core`

- Dependencies: kotlinx-serialization, kotlinx-datetime.
- Contents: request/response DTOs, enums (`Currency`, `InputType`, `ParseModality`, `TrendGranularity`), `ApiRoutes` path constants, the `ApiErrorResponse` envelope, default-category data and translations.
- Targets: commonMain only, pure Kotlin.
- Purpose: the API contract that `:server` and `:app:shared` both import, so the two cannot drift.

#### `:server`

- Dependencies: `:core`, Ktor Server, Exposed, HikariCP, Koin, the PostgreSQL driver.
- Targets: JVM only.
- Purpose: the backend API and AI orchestration.

#### `:app:shared`

- Dependencies today: `:core`, Compose Multiplatform, lifecycle-viewmodel.
- Targets: androidMain, iosMain, with a jvmMain entry for desktop hot reload.
- Purpose: the shared client UI, built as an Android library and an iOS framework. The networking, local-storage, and DI layers described under Client Architecture are the planned design; this module currently holds Compose scaffolding only.

#### `:app:androidApp` and `:app:desktopApp`

- Thin entry points that host `:app:shared`. The desktop app exists for Compose Hot Reload while editing shared UI.

#### `:build-logic`

- Convention plugins (`skilky.kotlin-jvm`, `skilky.kotlin-multiplatform`, `skilky.android-app`, `skilky.detekt`, `skilky.spotless`) applied across the other modules.

---

## Client Architecture

The client is not built yet. Per `docs/implementation-phases.md` the backend is
through Phase 7; `:app:shared` still holds the Compose Multiplatform template.
This section is the intended design, not the current state.

### MVVM+ pattern

Every screen follows unidirectional data flow: **user action → ViewModel method → new state → UI**.

```mermaid
flowchart LR
    UI[Screen] -->|method call| VM[ViewModel]
    VM -->|new state| STATE[UiState]
    STATE --> UI
    VM -->|one-shot| EFFECT[Effect]
    EFFECT --> UI
```



Each feature screen defines two types and one ViewModel:

- **UiState** — immutable `data class` holding all rendered state (`isSubmitting`, `items`, `error`, ...).
- **Effect** — `sealed interface` for one-shot signals the screen consumes once (`NavigateToHome`, `ShowSnackbar`, ...).
- **ViewModel** — exposes `state: StateFlow<UiState>` and `effects: Flow<Effect>` (backed by a `Channel`). User actions are public methods, named `onEmailChange(value)`, `onSubmit()`, `onSignOut()`, etc. The stateful screen wires those as method references into the stateless `*ScreenContent`, which is what `@Preview` and tests render.

We considered a strict MVI variant with a `sealed interface Intent` and a single `onIntent(intent)` dispatcher. It paid for itself only with event replay, middleware, or a centralized reducer, none of which are on the roadmap. Direct methods are navigable from screen to handler, refactorable by the IDE, and read like ordinary Kotlin.

### Package Structure

```
app/shared/src/commonMain/kotlin/com/vstorchevyi/skilky/
├── App.kt                          # Root composable, theme, nav host
├── di/                              # Koin modules
├── navigation/                      # NavHost, Screen sealed class
├── ui/
│   ├── theme/                       # Material 3 theme
│   ├── screens/                     # Feature screens (*Screen + *ScreenContent + ViewModel + UiState + Effect)
│   │   ├── auth/
│   │   ├── home/
│   │   ├── input/
│   │   ├── expenses/
│   │   ├── analytics/
│   │   ├── categories/
│   │   └── settings/
│   └── components/                  # Reusable composables
├── data/
│   ├── local/                       # Room database, DAOs, entities
│   ├── remote/                      # Ktor client, API services, token storage
│   ├── repository/                  # Data access layer
│   └── sync/                        # Offline input queue + sync manager
└── util/                            # Platform abstractions
```

### Client Data Flow

```mermaid
flowchart TD
    UI[UI Layer]
    REPO[Repository]
    ROOM[(Room DB)]
    API[Remote API]
    SYNC[Sync Manager]
    QUEUE[(Input Queue)]
    NET{Online?}

    UI -->|method call| REPO
    REPO -->|read/write| ROOM
    REPO -->|online requests| API
    API -->|parse + save| ROOM
    ROOM -->|Flow| UI

    SYNC --> QUEUE
    SYNC --> NET
    NET -->|Yes| API
    NET -->|No| WAIT[Wait for connectivity]
    WAIT --> NET

    style UI fill:#4CAF50,color:#fff
    style REPO fill:#2196F3,color:#fff
    style ROOM fill:#9C27B0,color:#fff
    style API fill:#FF9800,color:#fff
    style SYNC fill:#F44336,color:#fff
    style QUEUE fill:#F44336,color:#fff
```



### Repository Data Strategies

Repositories coordinate between local (Room) and remote (Ktor) data sources:


| Strategy         | How it works                                                            | Used for                   |
| ---------------- | ----------------------------------------------------------------------- | -------------------------- |
| **networkFirst** | Fetch from server → cache in Room → fallback to Room on network failure | Expenses, analytics        |
| **cacheFirst**   | Return Room data immediately → refresh from server in background        | Categories (rarely change) |
| **localOnly**    | Read/write Room directly, enqueue sync                                  | Offline input queue        |


### Error Handling

Custom exception hierarchy for networking:

```
NetworkException (sealed)
├── Unauthorized (401)     → trigger token refresh or redirect to login
├── Forbidden (403)        → show permission error
├── NotFound (404)         → show "not found" state
├── ServerError (5xx)      → show generic server error, allow retry
├── NetworkUnavailable     → show offline state
└── Timeout                → show timeout error, allow retry
```

API calls are wrapped in `AppResult<T>` (sealed class in `:shared:core`):

- `AppResult.Success<T>` — contains data
- `AppResult.Error` — contains `NetworkException`

### Key Patterns

- **Architecture:** MVVM+. Screens call methods on the ViewModel, observe `StateFlow<UiState>`, and collect a `Flow<Effect>` for navigation and snackbars.
- **Navigation:** Official JetBrains Navigation Compose with type-safe @Serializable routes
- **DI:** Koin with compose-viewmodel integration (`koinViewModel()`)
- **Online flow:** Input → server parses → preview → user confirms → save to Room + server
- **Offline flow:** Input → raw data saved to InputQueue → when online: server parses → auto-save (no preview backlog)
- **Dedup:** `clientId` (UUID) on `POST /expenses` prevents duplicates on retry
- **Token storage:** DataStore (Preferences) — KMP-native, no expect/actual needed
- **Ktor engines:** OkHttp on Android (HTTP cache, interceptors), Darwin on iOS (URLSession, NWPath)

---

## Server Architecture

### Package Structure

```
server/src/main/kotlin/com/vstorchevyi/skilky/
├── Application.kt          # module(): installs plugins, registers routes
├── ai/                     # Ollama client, prompt templates, parsing orchestration
├── config/                 # typed AppConfig over HOCON
├── db/                     # DatabaseFactory + Exposed table definitions
├── di/                     # Koin modules
├── domain/model/           # internal record types
├── errors/                 # ApiException hierarchy
├── eval/                   # offline parse-quality eval harness
├── plugins/                # one file per Ktor plugin install
├── repository/             # suspend functions over Exposed
├── routes/                 # one file per feature group
└── security/               # password/token hashing, JWT, validators
```

### AI parsing

The server calls a self-hosted Ollama instance over HTTP. One model
(`gemma4:e4b`, set via `skilky.ai.model`) handles text, audio, and receipt
images, so there is no separate speech-to-text or vision service.

```mermaid
flowchart LR
    CLIENT[Mobile App] --> KTOR[Ktor Server]
    KTOR -->|text| OLLAMA[Ollama gemma4:e4b]
    KTOR -->|audio| OLLAMA
    KTOR -->|receipt image| OLLAMA
    OLLAMA -->|parsed items JSON| KTOR
```

`TextParsingService` is the single orchestrator behind `/parse/text`,
`/parse/audio`, and `/parse/receipt`. It builds the prompt, calls `OllamaClient`,
and maps the model's JSON reply into `ParsedExpenseItem`s. `CachedCategoryLoader`
feeds the user's categories into the prompt as hints.

The earlier plan for an `AiParsingService` interface with swappable
implementations was dropped; there is one concrete service. A cloud-provider
adapter (Gemini, OpenAI) is tracked as Post-MVP Phase 16.

---

## Auth Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant S as Ktor Server
    participant DB as PostgreSQL

    Note over C,S: Registration
    C->>S: POST /auth/register {email, password, name}
    S->>DB: Insert user (bcrypt hash)
    S->>DB: Insert refresh token
    S-->>C: {jwt, refreshToken, user}
    C->>C: Store tokens securely

    Note over C,S: Normal API Call
    C->>S: GET /expenses (Authorization: Bearer jwt)
    S->>S: Verify JWT
    S->>DB: Query expenses
    S-->>C: [expenses]

    Note over C,S: Token Refresh (JWT expired)
    C->>S: GET /expenses (expired JWT)
    S-->>C: 401 Unauthorized
    C->>S: POST /auth/refresh {refreshToken}
    S->>DB: Validate refresh token
    S-->>C: {newJwt, newRefreshToken}
    C->>S: GET /expenses (new JWT)
    S-->>C: [expenses]
```



- JWT lives 7 days, refresh token lives 90 days
- Refresh token rotates on each use
- Password change invalidates all refresh tokens (kills all sessions)
- Same auth system for hosted and self-hosted (self-hosted users just create one account)
- JWT payload: userId, email, iat, exp

---

## Offline Input Queue

Since AI parsing happens on the server, the client can only store **raw input** (text, audio, images) when offline — not structured expenses.

```mermaid
sequenceDiagram
    participant U as User
    participant UI as UI
    participant Q as Input Queue (Room)
    participant SM as Sync Manager
    participant API as Server API
    participant Room as Room DB

    Note over U,API: Offline — User enters expenses
    U->>UI: Types "milk 45, bread 22"
    UI->>Q: Save raw input (type=TEXT, text="milk 45, bread 22")
    UI-->>U: Show pending card with raw text

    Note over U,API: Back online
    SM->>SM: Detect connectivity restored
    SM->>Q: Read pending items (FIFO)
    SM->>API: POST /parse/text {text: "milk 45, bread 22"}
    API-->>SM: {items: [Milk 45, Bread 22]}
    SM->>API: POST /expenses {items: [...]}
    API-->>SM: {items: [created]}
    SM->>Room: Save parsed expenses locally
    SM->>Q: Delete processed queue item
    UI-->>U: Pending card replaced with real expense items
```



- Raw input (text/audio/image) stored locally in InputQueue table
- When online: send to `/parse/*` → auto-save with AI categories → no user review backlog
- `clientId` (UUID) on `POST /expenses` ensures dedup on retries
- User can review and edit auto-saved items at their own pace
- On app launch (online): process queue + pull latest expenses from server

---

## Expense Input Flow

```mermaid
flowchart LR
    START((Start))
    INPUT[Quick Entry Bar]
    TEXT[Type text]
    MIC[Mic]
    CAM[Camera]

    SEND[Send to Backend]
    OLLAMA[Ollama gemma4:e4b]

    PREVIEW[Preview Sheet]
    EDIT[Edit items]
    CONFIRM[Confirm]
    SAVE[Save + Sync]

    START --> INPUT
    INPUT --> TEXT
    INPUT --> MIC
    INPUT --> CAM

    TEXT --> SEND
    MIC --> SEND
    CAM --> SEND
    SEND --> OLLAMA

    OLLAMA --> PREVIEW

    PREVIEW --> EDIT
    EDIT --> CONFIRM
    PREVIEW --> CONFIRM
    CONFIRM --> SAVE
```



---

## Docker Deployment

```mermaid
graph TB
    subgraph "Host Machine"
        DC[docker-compose.yml]

        subgraph Network
            BE[Ktor Backend]
            PG[(PostgreSQL)]
            OL[Ollama]
        end

        subgraph Volumes
            V1[postgres-data]
            V2[ollama-data]
        end
    end

    DC --> BE
    DC --> PG
    DC --> OL

    PG --> V1
    OL --> V2

    BE -- JDBC --> PG
    BE -- HTTP --> OL

    INTERNET((Internet)) -- 8080 --> BE
```



