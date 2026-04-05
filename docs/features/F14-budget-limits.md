# F14: Budget Limits / Goals

**Priority:** Post-MVP
**Depends on:** F05 (Expense CRUD), F06 (Categories), F08 (Analytics)

## Summary

Users set monthly spending limits per category. The app shows progress toward the limit and alerts when approaching or exceeding it.

## User Stories

- As a user, I want to set a monthly budget for food so I can control my spending
- As a user, I want to be notified when I'm close to my limit

## Key Features

- Set monthly limit per category (e.g., Food: 5000 UAH/month)
- Set overall monthly budget
- Progress bar on Home screen showing budget usage
- Warning at 80% threshold
- Alert when exceeded
- Push notification option

## Data Model

```
BudgetLimit:
  id, userId, categoryId (nullable = overall), amount, currency, period (monthly)
```

## UI

- Budget settings screen (set limits per category)
- Progress indicators on Home and Analytics screens
- Category list shows spending vs limit
