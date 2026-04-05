# F08: Analytics Dashboard

**Priority:** MVP
**Phase:** 7
**Depends on:** F05 (Expense CRUD), F06 (Categories)

## Summary

Users can view spending analytics: monthly summary, category breakdown (pie chart), and spending trend over time.

## User Story

As a user, I want to see where my money goes so I can understand my spending patterns and make better financial decisions.

## Analytics Screen

```
┌──────────────────────────────────────────┐
│  March 2026                    [< >]     │
│                                          │
│  Total: 8,450 ₴                          │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │     Monthly Spending Trend         │  │
│  │  ▁▃▅▇█▅▃▁  (bar/line chart)       │  │
│  │  Oct Nov Dec Jan Feb Mar           │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │     Category Breakdown             │  │
│  │         ╭───╮                      │  │
│  │       ╭─┤   ├─╮ (pie chart)       │  │
│  │       ╰─┤   ├─╯                   │  │
│  │         ╰───╯                      │  │
│  │                                    │  │
│  │  🍽 Food         3,200 ₴   37.9%   │  │
│  │  🚗 Transport    1,850 ₴   21.9%   │  │
│  │  🎬 Entertainment 1,200 ₴  14.2%   │  │
│  │  ...                               │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

## Charts

### Spending Trend (Bar/Line Chart)
- Supports weekly and monthly granularity (toggle in UI)
- Weekly: useful for spotting short-term patterns within a month
- Monthly: useful for seeing long-term spending habits
- Tappable bars to see period detail
- Currency selectable

### Category Breakdown (Pie/Donut Chart)
- Shows spending by category for selected period
- Each slice colored by category color
- Legend below with category name, amount, percentage
- Tappable slices to filter expense list

## Period Selector

- Month (default): `< March 2026 >`
- Swipe or arrows to navigate months
- Future: quarter, year, custom range

## API Endpoints

- `GET /api/v1/analytics/monthly?year=2026&month=3&currency=UAH`
- `GET /api/v1/analytics/breakdown?from=2026-03-01&to=2026-03-31&currency=UAH`
- `GET /api/v1/analytics/trend?granularity=monthly&periods=6&currency=UAH`
- `GET /api/v1/analytics/trend?granularity=weekly&periods=6&currency=UAH`

## Charting Implementation

Options (pick one during implementation):
- **Compose Canvas** — custom drawing, full control, no dependency
- **KMP charting library** — if a stable one exists at implementation time

## Acceptance Criteria

- [ ] Monthly total displayed correctly
- [ ] Trend chart supports weekly and monthly granularity
- [ ] Trend chart shows last 6 periods of data
- [ ] Pie chart shows category breakdown
- [ ] Percentages add up to 100%
- [ ] Period navigation works (month forward/back)
- [ ] Tapping category in breakdown filters expense list
- [ ] Currency selectable for analytics
