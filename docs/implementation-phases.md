# Implementation Phases

Ordered for a solo developer learning as they go. Each phase has a clear goal, deliverables, and a checkpoint to verify before moving on.

---

## Phase 1: Project Skeleton

**Goal:** Monorepo compiles, server responds to health check, app shows a screen.

### Deliverables

- Set up CMP project via Android Studio template
- Configure root Gradle: version catalog (`libs.versions.toml`), `settings.gradle.kts`, convention plugins (`build-logic/`)
- Configure Gradle build optimization in `gradle.properties`: parallel builds, configuration cache, JVM heap (4 GB), Kotlin incremental compilation
- Configure static analysis in `build-logic`:
  - Detekt plugin + config at `config/detekt/detekt.yml`
  - Ktlint via Spotless plugin + `.editorconfig` at repo root
  - Applied to all modules via convention plugins
- Create `:shared:models` with `Currency` enum + `ApiRoutes` object
- Create `:shared:core` with `DateUtils` placeholder
- Create `:server` with minimal Ktor application (`GET /health` → `{"status": "ok"}`)
- Create `docker/docker-compose.yml` with just postgres + backend services
- Set up `.github/workflows/build.yml` with lint + build jobs

### Checkpoint

- `./gradlew build` passes with no errors
- `./gradlew detekt` passes with no issues
- `./gradlew spotlessCheck` passes (formatting correct)
- `./gradlew :server:run` starts Ktor on port 8080
- `curl localhost:8080/health` returns `{"status": "ok"}`
- `./gradlew :composeApp:assembleDebug` produces installable APK
- Android app launches and shows a placeholder screen
- GitHub Actions lint + build workflow passes

---

## Phase 2: Auth System

**Goal:** Users can register and login. JWT works end-to-end.

### Deliverables

- Server: `UsersTable`, `RefreshTokensTable` (Exposed)
- Server: `DatabaseFactory` — schema creation, connection setup
- Server: `PasswordHasher` (BCrypt), `JwtTokenProvider`, `JwtConfig`
- Server: `AuthRoutes` — `/register`, `/login`, `/refresh`
- Shared: Auth DTOs — `LoginRequest`, `RegisterRequest`, `AuthResponse`
- Client: `LoginScreen` + `RegisterScreen` + `AuthViewModel`
- Client: `ApiClient` (Ktor Client factory), `TokenStorage` (expect/actual)
- Client: `AuthInterceptor` — attach JWT, auto-refresh on 401
- Client: Basic navigation (login → home, redirect to login if unauthenticated)

### Checkpoint

- Register new user via app → see Home screen
- Close app → reopen → still logged in (token persisted)
- Token expires → app auto-refreshes without user interaction
- Invalid credentials → error shown on Login screen

---

## Phase 3: Expense CRUD + Categories

**Goal:** Manually add, view, edit, delete expenses with categories.

### Deliverables

- Shared: All expense/category DTOs (`ExpenseRequest`, `ExpenseResponse`, `CategoryDto`, etc.)
- Shared: `DefaultCategories` object with 9 predefined categories
- Server: `CategoriesTable`, `ExpensesTable` (Exposed)
- Server: `CategoryRoutes` — GET, POST, PUT, DELETE
- Server: `ExpenseRoutes` — GET (paginated), POST (batch), PUT, DELETE
- Server: Seed default categories on user registration
- Client: Room database — `SkilkyDatabase`, `ExpenseDao`, `CategoryDao`, entities
- Client: `ExpenseRepository`, `CategoryRepository`
- Client: `HomeScreen` — expense list grouped by date + manual text entry
- Client: `ExpenseDetailScreen` — view/edit single expense
- Client: `CategoriesScreen` — view/add/edit/delete categories

### Checkpoint

- Add expense manually (name + amount + category picker) → appears in list
- Edit expense → changes reflected
- Delete expense → removed from list
- Create custom category → appears in picker
- Server data visible across app restart

---

## Phase 4: AI Text Parsing

**Goal:** Quick-entry bar sends text to server, AI parses it, user previews and confirms.

> **Update (2026-05):** Phase 4 server work landed using `gemma4:e4b` as the model. The originally planned `AiParsingService` interface was skipped (no concrete second implementation in sight); the concrete `TextParsingService` is wired directly. `llama3.2` is no longer the default.

### Deliverables

- Server: `OllamaClient` — Ktor Client calling Ollama's `/api/chat` endpoint
- Server: `PromptTemplates` — system prompt for structured expense extraction
- Server: `TextParsingService` — orchestrator (single concrete class)
- Server: `ParseRoutes` — `POST /parse/text`
- Client: `QuickEntryBar` composable — text field with submit
- Client: `ParsePreviewSheet` — bottom sheet with editable parsed items
- Client: `InputViewModel` — orchestrate: submit text → show preview → confirm → save
- Docker: Add `ollama` service to docker-compose.yml
- Documentation: how to pull the text model (`ollama pull gemma4:e4b`)

### Checkpoint

- Type "milk 45, bread 22" → preview shows 2 items with correct names, amounts, categories
- Edit an item in preview → changes saved correctly
- "Save All" → items appear in expense list
- Works in both English and Ukrainian input

---

## Phase 5: Audio + Receipt Parsing

**Goal:** Voice and image input work end-to-end.

> **Update (2026-05):** Server side landed alongside Phase 4. **No Whisper service, no separate vision model** — `gemma4:e4b` handles audio (WAV 16 kHz mono, ≤30-60 s) and receipt images natively via Ollama. Saves one container and one model pull. Client work (recorders, picker, UI) is still pending.

### Deliverables

- ~~Server: `WhisperService` — Ktor Client calling Speaches `/v1/audio/transcriptions`~~ (not needed)
- Server: `ParseRoutes` — `POST /parse/audio`, `POST /parse/receipt`
- ~~Server: Receipt vision via Ollama with LLaVA/Moondream model~~ (Gemma 4 instead)
- Client: `AudioRecorder` (expect/actual — Android `AudioRecord`+WAV header, iOS `AVAudioRecorder` PCM)
- Client: Audio button in `QuickEntryBar` — record, show recording indicator, send to server
- Client: Camera/gallery image picker (expect/actual)
- Client: Camera button in `QuickEntryBar` — capture/pick image, send to server
- ~~Docker: Add `whisper` (Speaches) service to docker-compose.yml~~ (not needed)
- ~~Documentation: how to pull vision model (`ollama pull llava`)~~ (Gemma 4 already covers vision)

### Checkpoint

- Tap mic → speak "taxi 120 hryvnias" → preview shows parsed item
- Tap camera → photograph receipt → preview shows extracted items
- Both flows end with successful save to expense list
- Ukrainian speech recognized correctly

---

## Phase 6: Offline Sync

**Goal:** App works fully offline, syncs automatically when back online.

### Deliverables

- Client: `SyncQueueEntity` + `SyncQueueDao` (Room)
- Client: Modify `ExpenseRepository` to always write locally first, enqueue sync item
- Client: `NetworkMonitor` (expect/actual — ConnectivityManager on Android, NWPathMonitor on iOS)
- Client: `SyncManager` — observe connectivity, process queue FIFO when online
- Server: `POST /expenses/sync` endpoint with `clientId`-based deduplication (unique index)
- Client: Pending indicator on unsynced expenses
- Client: Full sync pull on app launch when online

### Checkpoint

- Enable airplane mode → add 3 expenses → they appear in list with pending indicator
- Disable airplane mode → pending indicators disappear
- Check server → all 3 expenses present
- Add same expenses again → no duplicates (clientId dedup)

---

## Phase 7: Analytics

**Goal:** Monthly summaries, category breakdowns, trend charts.

### Deliverables

- Shared: `MonthlySummaryResponse`, `CategoryBreakdownResponse`, `TrendPoint`
- Server: `AnalyticsService` with aggregate SQL queries
- Server: `AnalyticsRoutes` — `/analytics/monthly`, `/analytics/breakdown`, `/analytics/trend`
- Client: `AnalyticsScreen` with period selector
- Client: `MonthlyChart` — bar or line chart of spending over time
- Client: `CategoryPieChart` — pie/donut chart of category breakdown
- Client: Charting via Compose Canvas or a KMP charting library

### Checkpoint

- View current month analytics → total matches sum of expenses
- Category pie chart → percentages add up to 100%
- Trend chart → shows last 6 months of data
- Change period → data updates correctly

---

## Phase 8: Multi-Currency + Localization

**Goal:** Full multi-currency support and English + Ukrainian UI.

### Deliverables

- Shared: Flesh out `Currency` enum with symbol, code, formatting rules
- Shared: `CurrencyFormatter` — locale-aware formatting ("45.00 ₴", "$22.00")
- Client: `CurrencySelector` composable
- Client: Default currency setting in `SettingsScreen`
- Server: Analytics endpoints accept `currency` param for aggregation
- Client: Compose Multiplatform string resources (`values/strings.xml` + `values-uk/strings.xml`)
- Client: Language picker in `SettingsScreen`

### Checkpoint

- Switch language to Ukrainian → entire UI in Ukrainian
- Switch back to English → UI in English
- Log expense in USD → displays correctly alongside UAH expenses
- Analytics show correct totals in selected currency

---

## Phase 9: Polish & Production

**Goal:** Ready for real daily use.

### Deliverables

- Server: `StatusPages` with proper error responses for all error codes
- Server: Rate limiting on auth endpoints (prevent brute force)
- Client: Error states on all screens (network error, server error, empty state)
- Client: Loading indicators during AI processing
- Client: Pull-to-refresh on expense list
- Client: Swipe-to-delete on expense items
- Docker: Production compose file with resource limits
- README with full setup instructions

### Checkpoint

- Kill server → app shows error state → restart server → app recovers
- AI processing → loading indicator shown → results appear
- Empty expense list → helpful empty state message
- All CI jobs pass green

---

## Post-MVP (Future Phases)

| Phase | Feature | Description |
|-------|---------|-------------|
| 10 | Budget Limits | Monthly limits per category, progress bars, alerts |
| 11 | Income Tracking | Log income, see net balance |
| 12 | Web Dashboard | Compose for Web (Wasm) or separate frontend, read-only stats |
| 13 | Wear OS | Add `wearApp` module, quick voice entry from wrist |
| 14 | Recurring Expenses | Auto-log rent, subscriptions |
| 15 | Export | CSV/PDF export |
| 16 | Cloud AI Adapter | Optional Gemini/OpenAI provider for users who prefer cloud |
