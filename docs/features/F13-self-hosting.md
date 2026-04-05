# F13: Self-Hosting

**Priority:** MVP
**Phase:** 1 (Docker setup) + ongoing

## Summary

Users can run their own instance of the entire backend stack with a single `docker compose up` command. No cloud dependencies.

## User Story

As a privacy-conscious user, I want to run the server on my own hardware so my financial data stays under my control.

## What Gets Self-Hosted

| Service | Purpose | Image |
|---------|---------|-------|
| Ktor Backend | API server | Custom (built in CI) |
| PostgreSQL | Database | postgres:17-alpine |
| Ollama | Text + vision AI | ollama/ollama:latest |
| Speaches (Whisper) | Speech-to-text | speaches:latest-cpu |

All 4 services run in Docker containers, connected via internal Docker network.

## Setup Flow

1. Install Docker
2. Get `docker-compose.yml` + `.env.example` (from repo or download)
3. `cp .env.example .env` → set passwords
4. `docker compose up -d`
5. Pull AI models (one-time): `docker exec skilky-ollama ollama pull llama3.2`
6. In the app: Settings → Server URL → `http://<server-ip>:8080`
7. Register account → start using

## Configuration

All configuration via environment variables (`.env` file):
- Database credentials
- JWT secret
- AI model names (swappable)
- Ports

See [deployment.md](../deployment.md) for full variable list.

## Client-Side Support

The app needs a **Server URL** setting:
- Default: hosted server URL (if we run one)
- User can change to their self-hosted server URL
- URL stored locally in settings
- All API calls go to the configured URL

## Data Ownership

- All data stored in user's PostgreSQL instance
- AI models run locally (no data sent to cloud)
- User controls backups (PostgreSQL volume)
- User can export data (future feature)

## Requirements

- Docker & Docker Compose
- 8 GB RAM minimum (Ollama needs memory)
- 10 GB disk (models + database)
- Optional: NVIDIA GPU for faster AI

## Acceptance Criteria

- [ ] `docker compose up -d` starts all 4 services
- [ ] Backend waits for healthy dependencies before starting
- [ ] Health checks work for all services
- [ ] App connects to self-hosted backend via configurable URL
- [ ] Registration works on self-hosted instance
- [ ] All features work identically on self-hosted
- [ ] Data persists across container restarts (volumes)
- [ ] `.env.example` documents all variables
