---
# isaac-la09
title: 'Cron: build frequencies for scheduled prompts'
status: todo
type: feature
priority: normal
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T16:53:26Z
parent: isaac-4e4b
blocked_by:
    - isaac-rqlc
---

cron/service.clj creates a session per job with :crew and no real selection. Adopt frequencies: a cron job specifies a frequencies map (which session/crew/tags, :create, :prefer + :with-* override) for its scheduled prompt. e.g. run a daily prompt on the most-recent session of crew X, or always-new.

Today: uses create-with-resolved-behavior! (override seam) but ad-hoc selection. Build the frequencies map from cron config and feed the shared core (isaac.session.frequencies). Blocked-by the frequencies rename.

## Deploy
Migrate zanebot cron job config -> frequencies map per job to the frequencies shape before flipping the schema (one-time, ops). Strict validation will fail loud if missed.

## Scenarios (2026-06-27) — 2 wiring scenarios; regression net = existing cron features
Today fire-job! creates a fresh session every tick (create-with-resolved-behavior! with nil key + :crew). New: build a frequencies map from the FLAT job config (cron.<job>.{crew,session,session-tags,create,prefer,with-*} alongside expr/prompt) and resolve via the shared core. No new step defs (cron loads agent session-steps + foundation fs-steps + its own scheduler step).

### S1 — selection wiring: resume the most-recent matching session
config: cron.health-check.{expr,prompt,crew=main,create=if-missing,prefer=recent}; sessions morning/last-night (main, updated-at); last-night has prior transcript; scheduler ticks -> last-night gets the prompt appended (proves cron resolves frequencies instead of always-create).

### S2 — override wiring: with-model flows to the scheduled turn
config: cron.health-check.{...,with-model=grover2}; grover2 -> echo-alt; scheduler ticks -> session-1 turn runs on echo-alt (proves :with-* override wires from config).

Scope: wiring only (per 4e4b). The create/prefer/reach matrix is rqlc's, not re-proven here. New steps: none.
