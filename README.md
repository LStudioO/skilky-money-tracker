# Skilky

[![Build](https://github.com/LStudioO/skilky-money-tracker/actions/workflows/build.yml/badge.svg)](https://github.com/LStudioO/skilky-money-tracker/actions/workflows/build.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://lstudioo.github.io/skilky-money-tracker/badge.json)](https://lstudioo.github.io/skilky-money-tracker/)

Skilky is a budget tracker for people who will not open a spreadsheet. You log spending the lazy way: type a line (`milk 45, bread 22`), say it, or snap a receipt. A local model figures out amounts and categories; you glance at a preview, tap confirm, and move on.

**Status:** Product spec and roadmap live under [`docs/`](docs/). The app code is still mostly skeleton work.

## Quickstart

Goal: `curl localhost:8080/api/v1/health` returns `{"status":"ok","version":"1.0.0"}` on a fresh clone.

Prereqs: JDK 21, Docker, ~12 GB free disk for the model and the DB.

On macOS, run Ollama natively so it can use Metal. Docker Desktop for macOS does not pass the
Apple GPU through to Linux containers, and receipt parsing can take several minutes on CPU only:

```bash
docker compose -f docker/docker-compose.yml up -d postgres
ollama pull gemma4:e4b
ollama serve # skip this when the Ollama menu bar app is already running
```

On Linux or Windows with a supported container GPU, start Postgres, Ollama, and the one-shot
model pull:

```bash
docker compose -f docker/docker-compose.yml up -d
```

The `ollama-pull` container downloads `gemma4:e4b` (about 10 GB) on first run, then exits. Tail it
until it does:

```bash
docker compose -f docker/docker-compose.yml logs -f ollama-pull
```

Then run the server:

```bash
./gradlew :server:run
```

And the smoke test, in another terminal:

```bash
curl localhost:8080/api/v1/health
# {"status":"ok","version":"1.0.0"}
```

`application.conf` ships dev defaults that match the docker-compose values, so no env vars are needed locally. For production, set `JWT_SECRET`, `REFRESH_TOKEN_PEPPER`, and `POSTGRES_PASSWORD` at minimum. Full env reference: [`docs/deployment.md`](docs/deployment.md).

## Project layout

```
core/              # DTOs and API contract (client + server)
server/            # Ktor backend
app/
  shared/          # Compose Multiplatform UI (Android library + iOS framework)
  androidApp/      # Android application
  desktopApp/      # Desktop app (Compose Hot Reload sandbox for shared UI)
  iosApp/          # Xcode iOS host
```

## Running builds

You need a normal JDK, Android Studio for Android work, and Xcode for iOS.

```bash
./gradlew :app:androidApp:assembleDebug   # Android
./gradlew :app:desktopApp:run             # Desktop (Compose Hot Reload)
./gradlew :server:run                     # Ktor server
```

iOS: open `app/iosApp` in Xcode, run the iosApp target.

For live UI edits in shared Compose code, run the desktop app with **Compose Hot Reload** from the IDE (see [Compose Hot Reload](https://kotlinlang.org/docs/multiplatform/compose-hot-reload.html)).

On Windows use `gradlew.bat` instead of `./gradlew`.

Build order and checkpoints: [`docs/implementation-phases.md`](docs/implementation-phases.md). Feature specs live in [`docs/features/`](docs/features/).
