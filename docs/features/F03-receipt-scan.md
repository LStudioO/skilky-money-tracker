# F03: Receipt Scan

**Priority:** MVP
**Phase:** 5
**Depends on:** F01 (Quick Text Entry), F05 (Expense CRUD)

## Summary

Users photograph a receipt (or pick from gallery), and the app sends the image to the backend where a vision model (LLaVA/Moondream via Ollama) extracts line items.

## User Story

As a user, I want to photograph my receipt so that I don't have to manually enter each line item from a grocery store trip.

## Flow

1. User taps the camera button in Quick Entry Bar
2. Camera opens (or long-press for gallery picker)
3. User takes photo of receipt
4. App sends image to backend
5. Loading indicator shown (receipt parsing may take longer than text)
6. Backend: image → Ollama vision model → structured items
7. App shows Parse Preview with extracted items
8. User confirms or edits

## Image Details

- **Camera:** platform-native camera capture
  - Android: ActivityResultContracts.TakePicture or CameraX
  - iOS: UIImagePickerController or PHPickerViewController
- **Gallery:** pick existing photo
- **Compression:** resize to max 1920px on longest side before upload (reduce bandwidth)
- **Format:** JPEG
- **Permissions:** Camera + storage permissions

## Backend

- **Endpoint:** `POST /api/v1/parse/receipt` (multipart: image file + currency)
- **AI Model:** Ollama with LLaVA or Moondream (vision model)
- **Prompt:** instructs model to extract line items as JSON
- **Response:** `{ items: [ParsedExpenseItem] }`

## Challenges

- Receipt quality varies (crumpled, faded, poor lighting)
- Some receipts are in Ukrainian, some in English
- Vision models may miss items or misread amounts
- Preview editing is critical here — user needs to verify AI output

## Edge Cases

- Camera permission denied → show explanation + settings link
- Blurry/unreadable image → "Couldn't read the receipt, try again with better lighting"
- Partial extraction → show what was found, let user add missing items
- Very long receipt → may need to photograph in sections (future enhancement)
- No items found → "No items detected, try a clearer photo"

## Acceptance Criteria

- [ ] Camera button visible in Quick Entry Bar
- [ ] Camera opens on tap
- [ ] Gallery picker available (long-press or separate option)
- [ ] Image compressed before upload
- [ ] Loading indicator during processing
- [ ] Extracted items shown in preview
- [ ] Amounts match receipt values
- [ ] Works with Ukrainian and English receipts
