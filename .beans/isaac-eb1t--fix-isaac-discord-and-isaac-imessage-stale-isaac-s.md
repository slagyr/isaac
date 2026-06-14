---
# isaac-eb1t
title: Fix isaac-discord and isaac-imessage stale isaac symbol references
status: completed
type: bug
priority: normal
created_at: 2026-06-05T14:38:26Z
updated_at: 2026-06-14T03:54:05Z
---

Both isaac-discord/src/isaac/comm/discord.clj and isaac-imessage/src/isaac/comm/imessage.clj reference removed isaac symbols (e.g. config/load-config) and fail at SCI analysis when running `bb spec` in those repos. Pre-existing, surfaced again during isaac-qqgv (phase 8 berth migration).

Companion to isaac-lyg0 (acp's equivalent). The three comm modules should probably be fixed together once the isaac API drift is fully audited.

## What to do

- Survey each ns for symbols that no longer exist (`config/load-config`, the home/state-dir helpers, etc.).
- Update to current isaac API (isaac.config.api/snapshot, isaac.config.paths/*, isaac.root/*).
- Re-run `bb spec` in each repo until green.

## Verified (2026-06-13)

bb spec green in both repos (isaac-imessage 27/0, isaac-discord 45/0), pins = 939aa468 (full SHA), no removed-symbol references (state-dir hits are param names; load-config-result is current API). The deferred bb spec-jvm server-app integration failures in isaac-discord are out of scope (runtime boot/reconcile drift, not stale symbols) — filed/follow-up separately.
