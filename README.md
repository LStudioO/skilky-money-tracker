# Skilky

[![Build](https://github.com/LStudioO/skilky-money-tracker/actions/workflows/build.yml/badge.svg)](https://github.com/LStudioO/skilky-money-tracker/actions/workflows/build.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://lstudioo.github.io/skilky-money-tracker/badge.json)](https://lstudioo.github.io/skilky-money-tracker/)

[![:core](https://img.shields.io/endpoint?url=https://lstudioo.github.io/skilky-money-tracker/badge-core.json)](https://lstudioo.github.io/skilky-money-tracker/)
[![:server](https://img.shields.io/endpoint?url=https://lstudioo.github.io/skilky-money-tracker/badge-server.json)](https://lstudioo.github.io/skilky-money-tracker/)
[![:app:shared](https://img.shields.io/endpoint?url=https://lstudioo.github.io/skilky-money-tracker/badge-app-shared.json)](https://lstudioo.github.io/skilky-money-tracker/)

Skilky is a budget tracker for people who will not open a spreadsheet. You log spending the lazy way: type a line (`milk 45, bread 22`), say it, or snap a receipt. A local model figures out amounts and categories; you glance at a preview, tap confirm, and move on.

**Status:** Product spec and roadmap live under [`docs/`](docs/). The app code is still mostly skeleton work.

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
