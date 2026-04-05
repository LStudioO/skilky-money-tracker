# F01: Quick Text Entry

**Priority:** MVP
**Phase:** 4
**Depends on:** F5 (Expense CRUD), F6 (Categories)

## Summary

Users type free-form text describing their purchases (e.g., "milk 45, bread 22, taxi 120") and the app sends it to the backend where an AI model parses and structures the input into individual expense items with suggested categories.

## User Story

As a user, I want to type my expenses in natural language so that I don't have to fill in forms for each item.

## Flow

1. User taps the text field in the Quick Entry Bar (Home screen, bottom)
2. Types: "milk 45, bread 22, taxi to work 120"
3. Presses Enter/Send
4. App shows loading indicator
5. Backend receives text → sends to Ollama with parsing prompt → returns structured items
6. App shows Parse Preview bottom sheet with:
  - Milk — 45.00 UAH — Food
  - Bread — 22.00 UAH — Food
  - Taxi to work — 120.00 UAH — Transport
7. User can edit any item or tap "Save All"
8. Items saved to local DB + synced to server

## Input Format

The parser should handle:

- Simple: "milk 45"
- Multiple: "milk 45, bread 22, taxi 120"
- With currency: "coffee $3.50"
- Natural: "bought milk for 45 hryvnias and bread for 22"
- Ukrainian: "молоко 45, хліб 22, таксі 120"
- Mixed: "milk 45 uah, coffee 3 usd"

## Backend

- **Endpoint:** `POST /api/v1/parse/text`
- **AI Model:** Ollama (llama3.2 or mistral)
- **Prompt:** System prompt instructs JSON output with name, amount, currency, category
- **Response:** `{ items: [ParsedExpenseItem] }`

## Edge Cases

- Empty input → do nothing
- AI can't parse → show error "Couldn't parse your input, try rephrasing"
- AI returns partial results → show what was parsed, let user add missing items manually
- Server unreachable → show error with retry option (or queue if offline mode is implemented)

## Acceptance Criteria

- Text field visible on Home screen at all times
- Submit triggers loading state
- Parsed items shown in preview with correct amounts and categories
- Each item editable before confirm
- "Save All" saves all items
- Works in English and Ukrainian
- Handles multiple items in one input

