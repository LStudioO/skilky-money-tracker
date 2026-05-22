# REST API Specification

## Base URL

```
/api/v1
```

All endpoints except auth require `Authorization: Bearer <jwt>` header.

---

## Authentication

### POST `/auth/register`

Create a new user account. Also seeds default categories for the user.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "displayName": "Vlad"
}
```

**Response (201):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "a1b2c3d4-e5f6-...",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "displayName": "Vlad",
    "defaultCurrency": "UAH"
  }
}
```

**Errors:**
- `409` — email already registered
- `422` — validation error (weak password, invalid email)

---

### POST `/auth/login`

**Request:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response (200):** Same shape as register response.

**Errors:**
- `401` — invalid credentials

---

### POST `/auth/refresh`

Rotate tokens. The old refresh token is invalidated.

**Request:**
```json
{
  "refreshToken": "a1b2c3d4-e5f6-..."
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "new-refresh-token-..."
}
```

**Errors:**
- `401` — invalid or expired refresh token

---

## Parsing (AI-powered)

### POST `/parse/text`

Send free-form text, get back structured expense items.

Requires `Authorization: Bearer <jwt>`. The server loads the caller's visible categories (system defaults + their custom ones) and lets the model pick from that list, so suggestions cover user-created categories like "Gym" or "Pets". Returns `503 AI_UNAVAILABLE` when the upstream Ollama service is unreachable or returns a malformed response. Returns `422 VALIDATION_ERROR` for blank text or text longer than 2000 characters. Returns `404` when the server has no `skilky.ai.*` block (or no database) configured.

**Request:**
```json
{
  "text": "milk 45, gym membership 500, taxi 120",
  "currency": "UAH"
}
```

**Response (200):**
```json
{
  "items": [
    {
      "name": "Milk",
      "amount": 45.0,
      "currency": "UAH",
      "suggestedCategoryId": 1,
      "suggestedCategoryName": "Food",
      "confidence": 0.95
    },
    {
      "name": "Gym membership",
      "amount": 500.0,
      "currency": "UAH",
      "suggestedCategoryId": 42,
      "suggestedCategoryName": "Gym",
      "confidence": 0.9
    },
    {
      "name": "Taxi",
      "amount": 120.0,
      "currency": "UAH",
      "suggestedCategoryId": 2,
      "suggestedCategoryName": "Transport",
      "confidence": 0.92
    }
  ]
}
```

`suggestedCategoryId` references a row the caller can see in `GET /categories` (either a system default or one of their own). `null` when the model picked a name that does not match any visible category. `suggestedCategoryName` is the raw string the model produced, kept so the client can show it as a hint even when no id matched ("the model thinks this is 'Pet food'").

---

### POST `/parse/audio`

Send a short voice note, get back the transcript plus structured expense items. Gemma 4 E4B handles both transcription and extraction in one model — no separate Whisper service.

Requires `Authorization: Bearer <jwt>`. Returns 422 for bad audio (see contract below), 503 when Ollama is unreachable, 404 when AI is not configured.

**Audio contract** (strict — clients must conform):
- WAV container with RIFF/WAVE header
- 16 kHz sample rate
- mono (1 channel)
- 16-bit PCM
- ≤ 10 MB
- ≤ ~30-60 seconds of audio (Gemma 4 E4B's input limit)

Each client platform records natively in this format. Android: `AudioRecord` with `PCM_16BIT` plus a manual 44-byte RIFF header. iOS / watchOS: `AVAudioRecorder` with `kAudioFormatLinearPCM`. WearOS: same as Android.

**Request:** `multipart/form-data`
- `file` — WAV bytes as above
- `currency` — currency code, e.g. `"UAH"`

**Response (200):**
```json
{
  "transcript": "milk forty five, taxi one twenty",
  "items": [
    {
      "name": "Milk",
      "amount": 45.0,
      "currency": "UAH",
      "suggestedCategoryId": 1,
      "suggestedCategoryName": "Food",
      "confidence": 0.88
    },
    {
      "name": "Taxi",
      "amount": 120.0,
      "currency": "UAH",
      "suggestedCategoryId": 2,
      "suggestedCategoryName": "Transport",
      "confidence": 0.9
    }
  ]
}
```

`transcript` is populated only for this endpoint. Same `suggestedCategoryId` / `suggestedCategoryName` semantics as `/parse/text`.

---

### POST `/parse/receipt`

Send a receipt photo, get back extracted line items. Same Ollama backend as `/parse/text` and `/parse/audio`; the system prompt steers the model to copy line item labels verbatim, treat the rightmost number per row as the line total, and ignore subtotals/tax/cash/change lines.

Requires `Authorization: Bearer <jwt>`. Returns 422 for non-JPEG/PNG input or files > 10 MB.

**Request:** `multipart/form-data`
- `file` — JPEG or PNG bytes, ≤ 10 MB
- `currency` — currency code

**Response (200):** Same `items` shape as `/parse/text` plus a `rawText` field with the full OCR transcription the model produced. `rawText` is useful for debugging when an `items` entry looks wrong — you can see exactly what the model read off the photo.

```json
{
  "items": [
    {
      "name": "ШАМПІН МАРИН ЦІЛІ 420Г RIO",
      "amount": 51.24,
      "currency": "UAH",
      "suggestedCategoryId": 1,
      "suggestedCategoryName": "Food",
      "confidence": 1.0
    }
  ],
  "rawText": "ТзОВ ТВК 'ЛЬВІВХОЛОД'\n... ШАМПІН МАРИН ЦІЛІ 420Г RIO 51.24 ..."
}
```

---

### POST `/parse/corrections`

Records what a parse endpoint returned against what the user kept after editing
the preview. Feeds prompt-quality tracking. The response has no body.

Requires `Authorization: Bearer <jwt>`. Rate-limited under the same budget as the
other parse endpoints. Returns `422 VALIDATION_ERROR` when `original` is empty.
Returns `404` when the server has no database configured.

**Request:**
```json
{
  "modality": "text",
  "currency": "UAH",
  "original": [
    { "name": "Milk", "amount": 45.0, "currency": "UAH", "suggestedCategoryId": 1, "suggestedCategoryName": "Food", "confidence": 0.95 }
  ],
  "final": [
    { "name": "Oat milk", "amount": 45.0, "currency": "UAH", "suggestedCategoryId": 1, "suggestedCategoryName": "Food", "confidence": 0.95 }
  ]
}
```

`modality` is `text`, `audio`, or `receipt` — which parse endpoint produced the
items. `original` is the unedited response; `final` is what the user saved, with
any renamed, re-priced, or recategorized items. `final` may be empty if the user
discarded everything.

**Response:** `204 No Content`

---

## Expenses

### GET `/expenses`

List expenses with pagination and filtering.

**Headers:** Optional `Accept-Language` — nested `category.name` for system defaults follows the same localization rules as `GET /categories`.

**Query params:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `from` | date (YYYY-MM-DD) | — | Start date filter |
| `to` | date (YYYY-MM-DD) | — | End date filter |
| `categoryId` | long | — | Filter by category |
| `page` | int | 0 | Page number |
| `size` | int | 50 | Page size (max 100) |

**Response (200):**
```json
{
  "items": [
    {
      "id": 42,
      "name": "Milk",
      "amount": 45.0,
      "currency": "UAH",
      "category": {
        "id": 1,
        "name": "Food",
        "icon": "material:restaurant",
        "color": "#4CAF50"
      },
      "note": null,
      "inputType": "TEXT",
      "date": "2026-03-21",
      "createdAt": "2026-03-21T10:30:00Z"
    }
  ],
  "total": 156,
  "page": 0,
  "size": 50
}
```

---

### POST `/expenses`

Batch create expenses.

**Request:**
```json
{
  "items": [
    {
      "name": "Milk",
      "amount": 45.0,
      "currency": "UAH",
      "categoryId": 1,
      "note": null,
      "inputType": "TEXT",
      "clientId": "550e8400-e29b-41d4-a716-446655440000",
      "date": "2026-03-21"
    }
  ]
}
```

**Response (201):**
```json
{
  "items": [
    {
      "id": 42,
      "name": "Milk",
      "amount": 45.0,
      "currency": "UAH",
      "category": { "id": 1, "name": "Food", "icon": "material:restaurant", "color": "#4CAF50" },
      "note": null,
      "inputType": "TEXT",
      "date": "2026-03-21",
      "createdAt": "2026-03-21T10:30:00Z"
    }
  ]
}
```

---

### PUT `/expenses/{id}`

Update a single expense.

**Request:** Same shape as a single item in POST body.

**Response (200):** Updated expense.

**Errors:**
- `404` — expense not found or not owned by user

---

### DELETE `/expenses/{id}`

**Response:** `204 No Content`

**Errors:**
- `404` — expense not found or not owned by user

---

## Deduplication

`POST /expenses` is idempotent via `clientId`. If a `clientId` already exists on the server, the existing expense is returned instead of creating a duplicate. This makes it safe for the offline sync manager to retry failed requests.

---

## Categories

### GET `/categories`

Returns system defaults + user's custom categories.

**Headers:** Optional `Accept-Language` (`en`, `uk`, …). Default category **names** are returned in the best matching locale; `nameKey` is always a stable ASCII slug for system rows (e.g. `food`) so clients can sync or match AI suggestions regardless of UI language.

**Response (200):**
```json
[
  {
    "id": 1,
    "name": "Food",
    "icon": "material:restaurant",
    "color": "#4CAF50",
    "isDefault": true,
    "nameKey": "food"
  },
  {
    "id": 10,
    "name": "Gym",
    "icon": "emoji:💪",
    "color": "#009688",
    "isDefault": false
  }
]
```

---

### POST `/categories`

Create a custom category.

**Request:**
```json
{
  "name": "Gym",
  "icon": "emoji:💪",
  "color": "#009688"
}
```

Icons are a prefixed string: `material:<name>` for built-in Material icons,
`emoji:<char>` for an emoji. Custom categories normally use `emoji:`.

**Response (201):** Created category.

---

### PUT `/categories/{id}`

Update a user-owned category. Cannot modify system defaults.

**Response (200):** Updated category.

**Errors:**
- `403` — cannot modify system default category
- `404` — not found

---

### DELETE `/categories/{id}`

Delete a user-owned category. Expenses in this category are reassigned to "Other".

**Response:** `204 No Content`

**Errors:**
- `403` — cannot delete system default category
- `404` — not found

---

## Analytics

### GET `/analytics/monthly`

Monthly spending summary.

**Query params:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `year` | int | current year | Year |
| `month` | int | current month | Month (1-12) |
| `currency` | string | user default | Currency to aggregate in |

**Response (200):**
```json
{
  "year": 2026,
  "month": 3,
  "currency": "UAH",
  "grandTotal": 8450.0,
  "totalByCategory": [
    { "category": "Food", "amount": 3200.0 },
    { "category": "Transport", "amount": 1850.0 },
    { "category": "Entertainment", "amount": 1200.0 },
    { "category": "Other", "amount": 2200.0 }
  ]
}
```

---

### GET `/analytics/breakdown`

Category breakdown for a date range.

**Query params:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `from` | date | start of month | Start date |
| `to` | date | today | End date |
| `currency` | string | user default | Currency |

**Response (200):**
```json
[
  {
    "category": "Food",
    "amount": 3200.0,
    "percentage": 37.9,
    "count": 42
  },
  {
    "category": "Transport",
    "amount": 1850.0,
    "percentage": 21.9,
    "count": 15
  }
]
```

---

### GET `/analytics/trend`

Spending trend over time, grouped by week or month.

**Query params:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `granularity` | string | `"monthly"` | `"weekly"` or `"monthly"` |
| `periods` | int | 6 | Number of periods to look back |
| `currency` | string | user default | Currency |

**Response (200) — monthly:**
```json
{
  "granularity": "monthly",
  "points": [
    { "year": 2025, "month": 10, "total": 7200.0 },
    { "year": 2025, "month": 11, "total": 8100.0 },
    { "year": 2025, "month": 12, "total": 9500.0 },
    { "year": 2026, "month": 1, "total": 6800.0 },
    { "year": 2026, "month": 2, "total": 7400.0 },
    { "year": 2026, "month": 3, "total": 8450.0 }
  ]
}
```

**Response (200) — weekly:**
```json
{
  "granularity": "weekly",
  "points": [
    { "weekStart": "2026-02-09", "total": 1850.0 },
    { "weekStart": "2026-02-16", "total": 2100.0 },
    { "weekStart": "2026-02-23", "total": 1620.0 },
    { "weekStart": "2026-03-02", "total": 2340.0 },
    { "weekStart": "2026-03-09", "total": 1980.0 },
    { "weekStart": "2026-03-16", "total": 2150.0 }
  ]
}
```

---

## Error Response Format

All errors follow this shape:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Email is already registered",
    "details": {}
  }
}
```

### Common Error Codes

| HTTP Status | Code | Description |
|-------------|------|-------------|
| 400 | `BAD_REQUEST` | Malformed request body |
| 401 | `UNAUTHORIZED` | Missing or invalid JWT |
| 403 | `FORBIDDEN` | Action not allowed (e.g., modify default category) |
| 404 | `NOT_FOUND` | Resource not found or not owned |
| 409 | `CONFLICT` | Duplicate resource (email, clientId) |
| 422 | `VALIDATION_ERROR` | Field validation failed |
| 500 | `INTERNAL_ERROR` | Server error |
| 503 | `AI_UNAVAILABLE` | Ollama not reachable or returned a malformed response |
