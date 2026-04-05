# F18: Recurring Expenses

**Priority:** Post-MVP
**Depends on:** F05 (Expense CRUD)

## Summary

Auto-log recurring expenses like rent, subscriptions, and utilities.

## Key Features

- Define recurring expense: name, amount, category, frequency (monthly/weekly/yearly)
- Auto-create expense entries on schedule
- Notification to confirm/adjust amount before auto-logging
- Manage recurring expenses in settings

## Data Model

```
RecurringExpense:
  id, userId, name, amount, currency, categoryId, frequency, nextDate, isActive
```
