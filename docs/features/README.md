# Feature Specifications

## MVP Features

| # | Feature | Phase | Status |
|---|---------|-------|--------|
| [F01](F01-quick-text-entry.md) | Quick Text Entry | 4 | Planned |
| [F02](F02-voice-entry.md) | Voice Entry | 5 | Planned |
| [F03](F03-receipt-scan.md) | Receipt Scan | 5 | Planned |
| [F04](F04-parse-preview.md) | Parse Preview | 4 | Planned |
| [F05](F05-expense-crud.md) | Expense CRUD | 3 | Planned |
| [F06](F06-categories.md) | Customizable Categories | 3 | Planned |
| [F07](F07-multi-currency.md) | Multi-Currency | 8 | Planned |
| [F08](F08-analytics.md) | Analytics Dashboard | 7 | Planned |
| [F09](F09-auth.md) | Auth System | 2 | Planned |
| [F10](F10-offline-queue.md) | Offline Queue | 6 | Planned |
| [F11](F11-multi-device.md) | Multi-Device | 2+6 | Planned |
| [F12](F12-bilingual-ui.md) | Bilingual UI (EN + UK) | 8 | Planned |
| [F13](F13-self-hosting.md) | Self-Hosting | 1 | Planned |

## Post-MVP Features

| # | Feature | Description |
|---|---------|-------------|
| [F14](F14-budget-limits.md) | Budget Limits/Goals | Monthly limits per category, alerts |
| [F15](F15-income-tracking.md) | Income Tracking | Log income, net balance |
| [F16](F16-web-dashboard.md) | Web Dashboard | Read-only stats web UI |
| [F17](F17-wear-os.md) | Wear OS | Quick entry from wrist |
| [F18](F18-recurring-expenses.md) | Recurring Expenses | Auto-log rent, subscriptions |
| [F19](F19-export.md) | Export | CSV/PDF export |
| [F20](F20-cloud-ai.md) | Optional Cloud AI | Pluggable Gemini/OpenAI adapter |

## Implementation Order

```
Phase 1: Skeleton + F13 (Docker)
Phase 2: F09 (Auth) + F11 (Multi-device foundation)
Phase 3: F05 (Expense CRUD) + F06 (Categories)
Phase 4: F01 (Text Entry) + F04 (Parse Preview)
Phase 5: F02 (Voice) + F03 (Receipt)
Phase 6: F10 (Offline) + F11 (Multi-device sync)
Phase 7: F08 (Analytics)
Phase 8: F07 (Multi-Currency) + F12 (Bilingual)
Phase 9: Polish
```
