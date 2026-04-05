# F20: Optional Cloud AI Adapter

**Priority:** Post-MVP
**Depends on:** F01 (Text Entry), F03 (Receipt Scan)

## Summary

Allow users to optionally use cloud AI providers (Google Gemini, OpenAI) instead of or alongside local Ollama models. For users who prefer faster/better parsing and don't mind cloud dependency.

## Key Features

- AI provider setting: Local (Ollama) or Cloud (Gemini/OpenAI)
- User provides their own API key
- Same `AiParsingService` interface, different implementation
- Server-side only — client code unchanged
- API key stored securely on server (per-user setting)

## Implementation

The `AiParsingService` interface already abstracts the AI provider:

```
AiParsingService
├── OllamaAiService (default)
├── GeminiAiService (optional)
└── OpenAiCompatibleService (optional)
```

User selects provider in Settings → server routes parsing requests to the configured service.

## Why Optional

- Core principle: no proprietary dependency
- But some users may prefer cloud models for better accuracy
- Self-hosted users probably want local-only
- Hosted service could offer cloud AI as a premium option
