# F02: Voice Entry

**Priority:** MVP
**Phase:** 5
**Depends on:** F01 (Quick Text Entry), F05 (Expense CRUD)

## Summary

Users tap a microphone button, speak their expenses, and the app sends the audio to the backend where Whisper converts speech to text, then Ollama parses the text into structured expense items.

## User Story

As a user, I want to speak my expenses so that I can log them without typing, especially when my hands are busy.

## Flow

1. User taps the mic button in Quick Entry Bar
2. UI shows recording indicator (pulsing dot, timer)
3. User speaks: "milk forty five, bread twenty two, taxi one hundred twenty"
4. User taps stop (or recording auto-stops after silence)
5. App sends audio file to backend
6. Loading indicator shown
7. Backend: audio → Whisper (STT) → transcript → Ollama (parsing) → structured items
8. App shows Parse Preview with transcript + parsed items
9. User confirms or edits

## Audio Details

- **Format:** WAV or M4A (platform-native recording format)
- **Platform implementation:** expect/actual
  - Android: MediaRecorder
  - iOS: AVAudioRecorder
- **Permissions:** Microphone permission required (request on first use)

## Backend

- **STT Endpoint:** Whisper/Speaches `POST /v1/audio/transcriptions`
- **Parse Endpoint:** `POST /api/v1/parse/audio` (multipart: audio file + language + currency)
- **Response:** `{ transcript: String, items: [ParsedExpenseItem] }`

## Language Support

- English and Ukrainian speech recognized by Whisper (medium/large models)
- Language can be auto-detected or specified by user setting
- Transcript shown in preview so user can verify speech was understood correctly

## Edge Cases

- Microphone permission denied → show explanation + settings link
- No speech detected → "No audio detected, try again"
- Whisper can't transcribe → show error
- Transcript OK but parsing fails → show transcript, let user edit as text and resubmit
- Background noise → Whisper handles this reasonably well

## Acceptance Criteria

- [ ] Mic button visible in Quick Entry Bar
- [ ] Recording indicator shown while recording
- [ ] Stop button works
- [ ] Transcript shown in preview
- [ ] Parsed items shown correctly
- [ ] Works in Ukrainian and English
- [ ] Microphone permission requested gracefully
