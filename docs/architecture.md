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
            O[Ollama]
            W[Whisper]
        end
    end

    A -- HTTPS --> K
    I -- HTTPS --> K
    K -- JDBC --> P
    K -- HTTP --> O
    K -- HTTP --> W

    style A fill:#4CAF50,color:#fff
    style I fill:#2196F3,color:#fff
    style K fill:#FF9800,color:#fff
    style P fill:#9C27B0,color:#fff
    style O fill:#F44336,color:#fff
    style W fill:#F44336,color:#fff
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



### Module Details

#### :shared:models

- **Dependencies:** kotlinx-serialization, kotlinx-datetime only
- **Contents:** All DTOs (request/response), enums (Currency, InputType), ApiRoutes constants, shared validation
- **Validation:** Value classes with `require()` for domain types (e.g. `Email`, password strength rules). Shared between client and server — validate once, enforce everywhere.
- **Targets:** commonMain only (pure Kotlin, no platform code)
- **Purpose:** API contract that both client and server import — ensures they can never drift

#### :shared:core

- **Dependencies:** :shared:models, kotlinx-coroutines, kotlinx-datetime
- **Contents:** CurrencyFormatter, DateUtils, AppResult sealed class, localization StringKeys
- **Targets:** commonMain only
- **Purpose:** Business logic shared between client and server

#### :composeApp

- **Dependencies:** :shared:core, Room KMP, Ktor Client, Koin, Navigation Compose, Coil, Compose MP
- **Targets:** androidMain, iosMain
- **expect/actual:** DatabaseFactory, NetworkMonitor, AudioRecorder
- **Purpose:** The mobile app

#### :server

- **Dependencies:** :shared:core, Ktor Server, Exposed, Koin, PostgreSQL driver
- **Targets:** JVM only
- **Purpose:** Backend API + AI orchestration

---

## Client Architecture

### MVI Pattern (Model-View-Intent)

Every screen follows unidirectional data flow: **Intent → ViewModel → State → UI**.

```mermaid
flowchart LR
    UI[Screen] -->|user action| INTENT[Intent]
    INTENT --> VM[ViewModel]
    VM -->|new state| STATE[State]
    STATE --> UI
    VM -->|one-shot| EFFECT[SideEffect]
    EFFECT --> UI
```



Each feature screen defines three things:

- **State** — immutable `data class` holding all UI state (`isLoading`, `items`, `error`, etc.)
- **Intent** — `sealed interface` of all user actions (`Submit`, `Delete`, `Refresh`, etc.)
- **SideEffect** — `sealed interface` for one-shot events (`NavigateTo`, `ShowSnackbar`, etc.)

The ViewModel exposes `StateFlow<State>` and `Channel<SideEffect>`. The Screen collects state and sends intents.

### Package Structure

```
composeApp/src/commonMain/kotlin/dev/skilky/tracker/app/
├── App.kt                          # Root composable, theme, nav host
├── di/                              # Koin modules
├── navigation/                      # NavHost, Screen sealed class
├── ui/
│   ├── theme/                       # Material 3 theme
│   ├── screens/                     # Feature screens (Screen + ViewModel + State + Intent)
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

    UI -->|intent| REPO
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

- **Architecture:** MVI — Intent → ViewModel → State → UI, with SideEffects for navigation/snackbars
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
server/src/main/kotlin/dev/skilky/tracker/server/
├── Application.kt                  # fun main(), embeddedServer(Netty)
├── config/                          # AppConfig, DatabaseConfig, JwtConfig
├── plugins/                         # Ktor plugins (Routing, Auth, CORS, etc.)
├── routes/                          # Route definitions by feature
├── service/                         # Business logic
│   └── ai/                          # AI service abstraction
├── repository/                      # Data access layer
├── db/tables/                       # Exposed table definitions
├── security/                        # Password hashing, JWT provider
└── util/                            # Extensions
```

### AI Service Layer

The server talks to Ollama and Whisper via HTTP (sibling Docker containers).

```mermaid
flowchart LR
    CLIENT[Mobile App] --> KTOR[Ktor Server]
    KTOR -->|text| OLLAMA[Ollama llama3.2]
    KTOR -->|image| OLLAMA_V[Ollama LLaVA]
    KTOR -->|audio| WHISPER[Whisper]

    WHISPER -->|transcript| KTOR
    KTOR -->|parsed text| OLLAMA
```



**AiParsingService interface:**

```
parseText(text, currency) → List<ParsedExpenseItem>
parseAudio(audioBytes, language, currency) → ParseResult (transcript + items)
parseReceipt(imageBytes, currency) → List<ParsedExpenseItem>
```

Default implementation uses Ollama + Whisper. The interface allows swapping in cloud providers (Gemini, OpenAI) in the future.

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
    WHISPER[Whisper STT]
    OLLAMA_T[Ollama text]
    OLLAMA_V[Ollama vision]

    PREVIEW[Preview Sheet]
    EDIT[Edit items]
    CONFIRM[Confirm]
    SAVE[Save + Sync]

    START --> INPUT
    INPUT --> TEXT
    INPUT --> MIC
    INPUT --> CAM

    TEXT --> SEND
    MIC --> WHISPER --> OLLAMA_T
    SEND --> OLLAMA_T
    CAM --> OLLAMA_V

    OLLAMA_T --> PREVIEW
    OLLAMA_V --> PREVIEW

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
            WH[Whisper]
        end

        subgraph Volumes
            V1[postgres-data]
            V2[ollama-data]
            V3[whisper-cache]
        end
    end

    DC --> BE
    DC --> PG
    DC --> OL
    DC --> WH

    PG --> V1
    OL --> V2
    WH --> V3

    BE -- JDBC --> PG
    BE -- HTTP --> OL
    BE -- HTTP --> WH

    INTERNET((Internet)) -- 8080 --> BE
```



