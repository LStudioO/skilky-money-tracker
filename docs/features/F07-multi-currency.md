# F07: Multi-Currency Support

**Priority:** MVP
**Phase:** 8

## Summary

Users can log expenses in different currencies. Each expense has its own currency. The user sets a default currency. Analytics can aggregate in any currency.

## User Story

As a user, I want to log expenses in different currencies (UAH for daily spending, USD for savings and travel) and see totals in my preferred currency.

## Supported Currencies (MVP)

| Currency | Code | Symbol |
|----------|------|--------|
| Ukrainian Hryvnia | UAH | ₴ |
| US Dollar | USD | $ |
| Euro | EUR | € |

More currencies can be added post-MVP by extending the `Currency` enum.

## How It Works

- Each expense has a `currency` field
- User sets a **default currency** in Settings (used as pre-fill)
- When entering expenses, currency defaults to the user's default
- User can override per-expense
- AI parsing respects the default currency and detects explicit currency mentions ("$3.50" → USD)

## Analytics

- Analytics endpoints accept a `currency` parameter
- Server aggregates in the requested currency
- Currency conversion: for MVP, use static rates or a simple conversion table. Post-MVP, integrate a rate API.

## Formatting

`CurrencyFormatter` (in `:shared:core`):
- `"45.00 ₴"` for UAH
- `"$22.00"` for USD
- `"€15.00"` for EUR

Formatting is locale-aware (symbol position, decimal separator).

## Acceptance Criteria

- [ ] Default currency configurable in Settings
- [ ] New expenses pre-fill with default currency
- [ ] Currency override available per expense
- [ ] AI detects explicit currency mentions in text
- [ ] Amounts display with correct currency symbol
- [ ] Analytics support aggregation in any currency
