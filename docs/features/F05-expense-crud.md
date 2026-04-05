# F05: Expense CRUD

**Priority:** MVP
**Phase:** 3

## Summary

Core expense management: create, read, update, delete expenses. This is the foundation that all other features build on.

## User Stories

- As a user, I want to view my expenses grouped by date so I can see my spending history
- As a user, I want to edit an expense if the AI categorized it wrong
- As a user, I want to delete an expense I entered by mistake

## Screens

### Expense List (Home + dedicated screen)

- **Home screen:** shows recent expenses (last 7 days) in a scrollable list
- **Expense List screen:** full list with filtering and pagination

#### List item:
```
│  🍽 Milk          Today      45.00 ₴   │
│  🚗 Taxi          Today     120.00 ₴   │
│  ── Yesterday ──────────────────────── │
│  🛒 Shopping      Mar 20    350.00 ₴   │
```

#### Features:
- Grouped by date (Today, Yesterday, date)
- Pull-to-refresh
- Swipe to delete
- Filter by:
  - Date range (from/to)
  - Category
- Pagination (load more on scroll)

### Expense Detail Screen

View/edit single expense:
- Name (editable text)
- Amount (editable number)
- Currency (dropdown)
- Category (category picker)
- Date (date picker)
- Note (optional text)
- Input type indicator (text/audio/image) — read-only
- Delete button

## Data Flow

1. Create: save to Room (local) → enqueue sync → sync to server when online
2. Read: always read from Room (single source of truth)
3. Update: update in Room → enqueue sync
4. Delete: mark deleted in Room → enqueue sync → remove from server

## API Endpoints

- `GET /api/v1/expenses` — paginated list with filters
- `POST /api/v1/expenses` — batch create
- `PUT /api/v1/expenses/{id}` — update
- `DELETE /api/v1/expenses/{id}` — delete

## Acceptance Criteria

- [ ] Expenses visible on Home screen grouped by date
- [ ] Full expense list with date and category filtering
- [ ] Tap expense → detail screen with all fields
- [ ] Edit expense → changes saved and reflected in list
- [ ] Delete expense → removed from list
- [ ] Pull-to-refresh works
- [ ] Swipe-to-delete works
- [ ] Pagination loads more items on scroll
