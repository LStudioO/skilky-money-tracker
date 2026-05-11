# Skilky

Skilky is a budget tracker for people who will not open a spreadsheet. You log spending the lazy way: type a line (`milk 45, bread 22`), say it, or snap a receipt. A local model figures out amounts and categories; you glance at a preview, tap confirm, and move on.

**Status:** Product spec and roadmap live under [`docs/`](docs/). The app code is still mostly skeleton work.

## Running builds

You need a normal JDK, Android Studio for Android work, and Xcode for iOS.

```bash
./gradlew :androidApp:assembleDebug   # Android
./gradlew :composeApp:run             # Desktop
./gradlew :server:run                 # Ktor server
```

iOS: open the `iosApp` folder in Xcode, run the iosApp target.

On Windows use `gradlew.bat` instead of `./gradlew`.

Build order and checkpoints: [`docs/implementation-phases.md`](docs/implementation-phases.md). Feature specs live in [`docs/features/`](docs/features/).
