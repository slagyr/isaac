---
# isaac-qvub
title: Server hot-reload watcher and reload lifecycle are visible in logs
status: in-progress
type: feature
priority: normal
created_at: 2026-06-26T16:24:30Z
updated_at: 2026-06-26T17:16:26Z
---

## Context

On a live system with `:server {:hot-reload true}`, it is currently hard to
answer basic operational questions:

- did the watcher start?
- which watcher implementation is active?
- did it observe the file write?
- did a reload begin?
- if not, where did the process stop?

Today the visible logs are mostly the tail outcome:
- `:config/reloaded`
- `:config/reload-failed`

There is no clear watcher-start or file-event breadcrumb.

## Draft acceptance

- On server boot with hot reload enabled, logs emit an info event like
  `:config.watch/started` with enough fields to identify the watcher:
  `:root`, `:config-root`, and impl (`:bb-fswatcher` vs `:jvm-watch-service`).
- When a config file changes, logs emit a debug breadcrumb like
  `:config.watch/change-detected` with the raw path and relative config path.
- Before reconciliation begins, logs emit an info event like
  `:config.reload/begin` with the triggering path.
- Existing `:config/reloaded` and `:config/reload-failed` remain, but the full
  sequence from watch -> reload attempt -> outcome is visible from logs alone.
- No behavior change to reload semantics beyond observability.

## Likely repo scope

- `isaac-server`

## Notes

This is the generic observability bean. It should not try to fix Discord
behavior directly; it exists so future runtime reload questions are answerable
without SSH archaeology.

## Approved scenarios

- `isaac-server/features/server/hot_reload_logging.feature:5`
  - `server start logs the hot-reload watcher it activated`
- `isaac-server/features/server/hot_reload_logging.feature:18`
  - `a config edit logs detection begin and successful reload`
- `isaac-server/features/server/hot_reload_logging.feature:38`
  - `an invalid config edit logs detection begin and reload failure`

## Decision (2026-06-26, Micah)

Keep this bean server-generic and observability-only:

- new feature file: `features/server/hot_reload_logging.feature`
- no new steps
- assert watcher startup, success-path reload, and failure-path reload
- do not mix in Discord-specific routing behavior; that belongs to `isaac-y1ak`
  and `isaac-enp1`

## Acceptance commands

- `cd isaac-server && bb features features/server/hot_reload_logging.feature`
