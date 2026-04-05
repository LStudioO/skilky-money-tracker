# F12: Bilingual UI (English + Ukrainian)

**Priority:** MVP
**Phase:** 8

## Summary

The entire app UI is available in English and Ukrainian. Users can switch language in Settings.

## User Story

As a Ukrainian user, I want to use the app in my native language.

## Implementation

### Client — Compose Multiplatform Resources (official)

Use the official JetBrains resource system:

```
composeApp/src/commonMain/composeResources/
├── values/
│   └── strings.xml          # English (default)
└── values-uk/
    └── strings.xml          # Ukrainian
```

Access in code: `stringResource(Res.string.home_title)`

- Compile-time safety — missing keys are caught at build time
- Supports plurals, string formatting, and locale switching
- Familiar API from Android development

### Server — Kotlin String Maps

Server error messages don't need the full resource system. Use a simple map in `:shared:core`:

```kotlin
object ServerStrings {
    private val en = mapOf("invalid_credentials" to "Invalid email or password", ...)
    private val uk = mapOf("invalid_credentials" to "Невірний email або пароль", ...)

    fun get(key: String, locale: String): String = ...
}
```

Server reads locale from `Accept-Language` header.

### What Gets Translated

- All UI labels (screen titles, buttons, placeholders)
- Error messages (client + server)
- Default category names
- Empty state messages
- Settings options

### What Stays in Original Language

- User-entered expense names (the user types in whatever language they want)
- AI transcripts (shown as-is)

### Language Picker

In Settings screen:
- English (default)
- Українська

Changing language applies immediately (no restart).

### AI Language

AI parsing works in both languages regardless of UI language setting:
- User can type in Ukrainian with English UI
- Whisper auto-detects language
- Ollama handles multilingual input

## Acceptance Criteria

- [ ] All UI text available in English
- [ ] All UI text available in Ukrainian
- [ ] Language picker in Settings
- [ ] Language change applies immediately
- [ ] Default category names translated
- [ ] Error messages translated
- [ ] AI input works in both languages regardless of UI language
