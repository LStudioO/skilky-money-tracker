# F06: Customizable Categories

**Priority:** MVP
**Phase:** 3

## Summary

Expenses are organized into categories. The app provides 9 predefined defaults, and users can create their own custom categories.

## Default Categories

| Category | Icon | Color | Description |
|----------|------|-------|-------------|
| Food | material:restaurant | #4CAF50 | Groceries, restaurants, coffee |
| Transport | material:directions_car | #2196F3 | Taxi, fuel, public transport |
| Housing | material:home | #9C27B0 | Rent, utilities, maintenance |
| Entertainment | material:movie | #E91E63 | Movies, games, events |
| Health | material:medical_services | #F44336 | Pharmacy, doctor, gym |
| Shopping | material:shopping_bag | #FF9800 | Clothes, electronics, gifts |
| Bills | material:receipt_long | #607D8B | Phone, internet, subscriptions |
| Education | material:school | #3F51B5 | Courses, books, supplies |
| Other | material:more_horiz | #795548 | Anything that doesn't fit above |

## User Stories

- As a user, I want predefined categories so I don't have to set up everything from scratch
- As a user, I want to create custom categories (e.g., "Gym", "Pet") for my specific needs
- As a user, I want to edit category names, icons, and colors

## Categories Screen

Accessible from Settings:

```
┌──────────────────────────────────────────┐
│  Categories                              │
├──────────────────────────────────────────┤
│  🍽 Food                    [default]    │
│  🚗 Transport               [default]    │
│  🏠 Housing                 [default]    │
│  🎬 Entertainment           [default]    │
│  🏥 Health                  [default]    │
│  🛒 Shopping                [default]    │
│  🧾 Bills                  [default]    │
│  🎓 Education               [default]    │
│  ••• Other                  [default]    │
│  ── Custom ────────────────────────────  │
│  💪 Gym                      [edit] [✕]  │
│  🐱 Pet                      [edit] [✕]  │
│                                          │
│  [+ Add Category]                        │
└──────────────────────────────────────────┘
```

## Icon System

Two icon types, stored as a prefixed string:

- **Default categories:** Material Icons (e.g., `"material:restaurant"`) — built into Compose, resolved at compile time
- **Custom categories:** Emoji (e.g., `"emoji:💪"`) — user picks from emoji picker when creating a category

The app renders based on prefix: `material:` → Material Icon composable, `emoji:` → Text composable.

## Rules

- Default categories cannot be deleted
- Default categories can be reordered (future) but not renamed
- Custom categories can be edited and deleted
- Deleting a custom category reassigns its expenses to "Other"
- Category icon selection: emoji picker (for custom categories)
- Category color selection: color picker or predefined palette

## AI Integration

When the AI parses expenses, it suggests a category from the user's full category list (defaults + custom). If the user has a "Gym" category, the AI should suggest it for "gym membership 500."

## Acceptance Criteria

- [ ] 9 default categories visible on first use
- [ ] Create custom category with name, icon, color
- [ ] Edit custom category
- [ ] Delete custom category → expenses reassigned to "Other"
- [ ] Default categories cannot be deleted
- [ ] Category picker available when creating/editing expenses
- [ ] AI uses full category list (including custom) for suggestions
