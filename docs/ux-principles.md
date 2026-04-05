# UX Principles — "Lazy" Budget Tracking

## Core Philosophy

The app succeeds only if users actually track their expenses. Every tap, every screen transition, every decision is friction that makes users less likely to log something. Design for the laziest possible user — including yourself at 11 PM after a long day.

## Principles

### 1. Home Screen = Entry Point

The quick-entry bar is always visible on the Home screen. No navigation required to log an expense. Open app → type/speak/photo → done.

### 2. One-Tap Confirm

After AI parses input, user sees a preview with parsed items. Default action: "Save All" — one tap. Editing individual items is possible but never required.

### 3. Smart Defaults

- **Currency:** auto-selected from user's default (configurable in settings)
- **Category:** AI suggests the most likely category
- **Date:** defaults to today
- **Input type:** text field is focused by default (fastest input)

The user should never need to fill in a dropdown or picker for the common case.

### 4. Batch Entry

One input can create multiple expenses:
- "milk 45, bread 22, taxi 120" → 3 items at once
- One receipt photo → N items
- One voice recording → N items

No need to add items one at a time.

### 5. Forgiving Editing

Nothing is permanent. User can always go back and edit any expense — change the amount, reassign the category, add a note. Mistakes are cheap.

### 6. Offline-First Feel

The app should feel instant. Writes go to local DB first. The user never waits for server response to see their expense in the list. Sync happens silently in the background.

Pending items show a subtle indicator (small icon) but are otherwise indistinguishable from synced items.

### 7. Progressive Disclosure

- Home screen: just the essentials (recent expenses + entry bar)
- Analytics: available but not in your face
- Settings: buried, rarely needed
- Categories management: accessible from settings, not a main tab

Don't overwhelm the user with features they use once a month.

---

## Screen Inventory

| Screen | Purpose | Entry Points |
|--------|---------|-------------|
| **Login** | Email/password auth | App launch (unauthenticated) |
| **Register** | Create account | Login screen link |
| **Home** | Recent expenses + quick entry | Main screen (bottom nav) |
| **Parse Preview** | Review AI-parsed items before saving | Bottom sheet after input submission |
| **Expense List** | Full filterable expense list | Bottom nav or Home "see all" |
| **Expense Detail** | View/edit single expense | Tap on expense in list |
| **Analytics** | Charts, summaries, trends | Bottom nav |
| **Categories** | Manage custom categories | Settings |
| **Settings** | Language, currency, server URL, account | Bottom nav or profile |

## Navigation Structure

```
Bottom Nav:
├── Home (default)
│   └── Parse Preview (bottom sheet overlay)
├── Expenses
│   └── Expense Detail
├── Analytics
└── Settings
    └── Categories
```

## Quick Entry Bar Design

The entry bar sits at the bottom of the Home screen, above the bottom navigation:

```
┌──────────────────────────────────────────┐
│  [  Type your expenses...    ] [🎤] [📷] │
└──────────────────────────────────────────┘
```

- **Text field:** always visible, focused on tap
- **Mic button:** starts audio recording, changes to stop button while recording
- **Camera button:** opens camera (or gallery picker via long-press)
- **Submit:** pressing Enter/Send on keyboard submits text; mic/camera submit automatically

## Parse Preview Design

A bottom sheet that slides up after input is processed:

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
│  🚗 Taxi                   120.00 UAH    │
│     Transport                [edit]      │
├──────────────────────────────────────────┤
│          [ Save All ]                    │
└──────────────────────────────────────────┘
```

- Each item shows: category icon, name, amount, currency, category name
- "edit" expands inline editing (change name, amount, category, date)
- "Save All" is the primary action — big, prominent button
- Dismiss (✕) cancels without saving

## Pending Expense Indicator

Unsynced expenses show a small cloud-with-arrow icon:

```
│  🍽 Milk          45.00 UAH  ☁↑  │
```

Once synced, the icon disappears. No user action required.
