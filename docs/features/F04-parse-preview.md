# F04: Parse Preview

**Priority:** MVP
**Phase:** 4
**Depends on:** F01 (Quick Text Entry)

## Summary

After any input (text, audio, image) is parsed by the AI, a bottom sheet appears showing the structured items. Users can review, edit, and confirm before saving.

## User Story

As a user, I want to review what the AI parsed before saving, so that I can catch and fix mistakes.

## Design

Bottom sheet overlay on Home screen:

```
┌──────────────────────────────────────────┐
│  Parsed 3 items                     [✕]  │
├──────────────────────────────────────────┤
│  🍽 Milk                    45.00 UAH    │
│     Food                     [edit]      │
├──────────────────────────────────────────┤
│  🍽 Bread                   22.00 UAH    │
│     Food                     [edit]      │
├──────────────────────────────────────────┤
│  🚗 Taxi to work           120.00 UAH    │
│     Transport                [edit]      │
├──────────────────────────────────────────┤
│  [+ Add item]                            │
│                                          │
│          [ Save All ]                    │
└──────────────────────────────────────────┘
```

## Interactions

- **Save All** — primary action, saves all items at once
- **Dismiss (✕)** — cancel, discard all parsed items
- **Edit** — expand item inline to edit:
  - Name (text field)
  - Amount (number field)
  - Currency (dropdown)
  - Category (category picker)
  - Date (date picker, defaults to today)
- **Delete item** — swipe or delete button removes single item from preview
- **Add item** — manually add an item the AI missed

## For Audio Input

When input was audio, show the transcript above the items:

```
┌──────────────────────────────────────────┐
│  Transcript:                             │
│  "milk forty five, bread twenty two"     │
├──────────────────────────────────────────┤
│  Parsed 2 items                     [✕]  │
│  ...                                     │
```

## States

- **Loading** — spinner while AI processes input
- **Success** — items shown as above
- **Error** — "Couldn't parse your input" with retry and manual entry options
- **Empty** — "No items found" with manual entry option

## Acceptance Criteria

- [ ] Bottom sheet appears after successful parsing
- [ ] All parsed items visible with name, amount, currency, category
- [ ] Each item editable inline
- [ ] Items can be deleted from preview
- [ ] Items can be added manually to preview
- [ ] "Save All" saves all items to DB
- [ ] Dismiss cancels without saving
- [ ] Loading state shown during AI processing
- [ ] Error state with retry option
- [ ] Transcript shown for audio input
