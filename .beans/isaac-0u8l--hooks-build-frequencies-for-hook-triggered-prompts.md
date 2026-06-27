---
# isaac-0u8l
title: 'Hooks: build frequencies for hook-triggered prompts'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T18:04:47Z
parent: isaac-4e4b
blocked_by:
    - isaac-rqlc
---

hooks.clj is the most ad-hoc consumer: session-key = (:session-key hook) or 'hook:<name>'; crew = (:crew hook) or 'main'; manual get-or-create. Adopt frequencies: a hook specifies a frequencies map for the session its prompt runs on.

Build the frequencies map from hook config and feed the shared core (isaac.session.frequencies). Blocked-by the frequencies rename.

## Deploy
Migrate zanebot hook config -> frequencies map per hook to the frequencies shape before flipping the schema (one-time, ops). Strict validation will fail loud if missed.

## Scenarios (2026-06-27) — 2 wiring scenarios; regression net = existing hooks.feature
Today (hooks.clj ~193-208): session-key = (:session-key hook) or "hook:<name>"; crew = (:crew hook) or "main"; :model -> :model-override; manual get-or-create. New: the hook frontmatter carries the FLAT frequencies set (crew/session/session-tags/create/prefer/with-*); build a frequencies map from it and resolve via the shared core. Hooks fire from POST /hooks/<name> against a real booted server (integration-flavored), turn runs async.

### S1 — selection wiring: resolve target via frequencies
hook config/hooks/garden.md frontmatter {crew: main, create: if-missing, prefer: recent}; sessions morning/last-night (main); last-night has prior transcript; POST /hooks/garden {leaves:3} -> rendered template lands on last-night (most-recent main), NOT the hardcoded hook:garden key.

### S2 — override wiring: with-model from frontmatter
frontmatter {crew: main, with-model: grover2}; existing main session garden-wk; POST -> the dispatched turn runs on echo-alt (proves :with-* override wires from frontmatter).

Regression net: existing hooks.feature scenarios (cwd defaults, auth) stay green — their crew:/session-key: frontmatter folds into the flat frequencies set (session-key -> session). Scope: wiring only (per 4e4b). New steps: none (hooks loads agent session-steps + server-test-support + its own /hooks POST step).
