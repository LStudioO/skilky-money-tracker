# F15: Income Tracking

**Priority:** Post-MVP
**Depends on:** F05, F08

## Summary

Users can log income in addition to expenses. Analytics show net balance (income - expenses).

## Key Features

- Log income with source, amount, currency, date
- Income categories (Salary, Freelance, Gift, Other)
- Net balance view: income - expenses per period
- Analytics include income data

## Data Model

Same as expenses but with a `type` field: INCOME or EXPENSE.
