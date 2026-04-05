# Skilky Money Tracker — Project Overview

## Vision

A **"lazy" budget tracking app** where users log expenses by typing, speaking, or photographing receipts. Local AI models parse and categorize input automatically. Users preview parsed items before confirming. Self-hostable via Docker — no dependency on proprietary AI services.

**Core principle:** Minimal friction. The fewer taps to log an expense, the more likely users actually do it.

## Target Platforms

- Android (primary)
- iOS
- Wear OS (future)
- Web dashboard — read-only stats (future)

## Languages

- English
- Ukrainian

## How It Works

1. User opens the app → sees recent expenses and a **quick-entry bar** at the bottom
2. User inputs expenses via **text** ("milk 45, bread 22"), **voice**, or **receipt photo**
3. Input is sent to the backend → AI parses and categorizes it
4. User sees a **preview** of parsed items → edits if needed → taps **Confirm**
5. Expenses are saved locally and synced to the server
6. User can view **analytics**: monthly summaries, category breakdowns, trends

## Self-Hosting

Users can run their own instance with a single command:

```bash
docker compose up -d
```

This starts: Ktor backend + PostgreSQL + Ollama (AI) + Whisper (speech-to-text). No cloud dependencies.

---

## Document Index

| Document | Description |
|----------|-------------|
| [Tech Stack](tech-stack.md) | All technologies with versions and rationale |
| [Architecture](architecture.md) | System diagrams, module structure, data flows |
| [Features](features/) | Individual feature specifications |
| [API Specification](api-spec.md) | REST API endpoints |
| [Data Models](data-models.md) | Database schemas (server + client) |
| [Deployment](deployment.md) | Docker setup, CI/CD, self-hosting guide |
| [Implementation Phases](implementation-phases.md) | Phased roadmap with checkpoints |
| [UX Principles](ux-principles.md) | Design guidelines for "lazy" tracking |
