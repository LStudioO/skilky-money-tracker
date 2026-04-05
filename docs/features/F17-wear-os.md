# F17: Wear OS

**Priority:** Post-MVP
**Depends on:** F01 (Text Entry), F02 (Voice Entry)

## Summary

Quick expense entry from the wrist. Voice-first interaction.

## Key Features

- Voice input: "coffee 65" from watch
- Quick text entry via watch keyboard
- Today's total visible on watch face (complication)
- Syncs via phone or directly to server

## Implementation

- Add `wearApp` module to monorepo
- Share `:shared:models` and `:shared:core`
- Wear-specific UI (small screen, voice-first)
- Compose for Wear OS
