# F16: Web Dashboard

**Priority:** Post-MVP
**Depends on:** F08 (Analytics), F09 (Auth)

## Summary

A read-only web interface for viewing expense statistics. No expense entry — just charts and summaries. Useful for quick checking from a desktop.

## Key Features

- Login with same account
- Monthly summary view
- Category breakdown charts
- Spending trends
- No expense creation/editing (read-only)

## Implementation Options

- **Compose for Web (Wasm)** — share UI code with mobile, experimental
- **Separate lightweight frontend** — React/Vue consuming the same REST API
- Decision deferred to implementation time based on CMP Wasm maturity
