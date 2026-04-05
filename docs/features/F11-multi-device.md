# F11: Multi-Device Support

**Priority:** MVP
**Phase:** 2 (auth) + 6 (sync)

## Summary

Users can access their data from multiple devices (phone + tablet, or multiple phones). Data syncs across devices via the server.

## User Story

As a user, I want to log expenses on my phone and see them on my tablet, using the same account.

## How It Works

- Same JWT auth system supports multiple sessions (multiple refresh tokens per user)
- All data goes through the server (source of truth for cross-device)
- Each device syncs independently using the offline sync mechanism
- `clientId` prevents duplicate creation across devices

## No Special Implementation Required

This feature is a natural outcome of:
- **F09 (Auth):** JWT + refresh tokens support concurrent sessions
- **F10 (Offline Sync):** each device syncs its queue and pulls updates

The main consideration: sync pull on app launch ensures fresh data from other devices.

## Acceptance Criteria

- [ ] Login on device A and device B with same account
- [ ] Add expense on device A → appears on device B after sync
- [ ] Edit expense on device A → change reflected on device B
- [ ] Delete expense on device A → removed on device B
- [ ] Both devices can be used simultaneously
