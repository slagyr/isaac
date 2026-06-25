---
# isaac-hrl1
title: 'Hot-reload: crew and model config changes not immediately visible to running sessions'
status: completed
type: bug
priority: normal
tags:
    - config
    - hot-reload
    - session
    - crew
    - model
created_at: 2026-06-25T12:00:00Z
updated_at: 2026-06-25T17:09:11Z
---

## Context / Motivation

With `:server {:hot-reload true}` (and the watcher/reloader active), changes to files under `config/crew/*.edn`, `config/models/*.edn`, or `config/providers/*.edn` should be picked up live by the system.

In practice this only works for fresh CLI invocations. Long-running sessions (ACP clients, persistent agents, high-turn sessions) continue to use stale crew defaults and model catalogs.

## Reproduction (observed on zanebot)

- Global config has `:hot-reload true`.
- Edit `config/crew/marvin.edn` to set `:model :gpt` (and ensure corresponding `config/models/gpt.edn` + chatgpt provider exist; fresh `isaac crew list` confirms `marvin` now shows `gpt-5.4 (chatgpt)`).
- In an existing long-running session for crew marvin ("tidy-comet"):
  ```
  /crew marvin
  switched crew to marvin
  /status
  ... Model claude-sonnet-4-6 (anthropic) ...
  /model gpt
  unknown model: gpt
  /model gpt-5.4
  unknown model: gpt-5.4
  ```
- Even after `/crew marvin` (which clears the per-session `:model`), the running session's resolved model and catalog remain the old ones.

Fresh one-off commands always see the updates; the persistent session does not.

## Root cause (initial hypothesis)

- Session behavior (crew, model, etc.) is resolved in `isaac.session.context/resolve-behavior*` (and `resolve-crew-context`) against the current process-wide config snapshot (`loader/snapshot` → atom populated by `load-config!` / `set-snapshot!`).
- Hot-reload (watcher in `isaac.config.change-source-*`, reloader in `isaac-server`, `runtime/reload!` → `load-config-result` + `set-snapshot!` + module reconcile) updates the snapshot for server-side reconciliation.
- Many session-hosting processes (ACP, dedicated agent loops, non-HTTP clients) perform an initial config load at startup. Subsequent turns pull the snapshot, but if no watcher/reloader thread is running in *that* process (or if entity changes to crew/models are not fully propagated through the session ctx/charge path), the `:models` map and crew-cfg seen by resolution stay stale.
- Per-session state in the store (the persisted `:model` value) further overrides the crew default until explicitly cleared.
- `/crew` updates the store entry (sets `:model nil`) and `/model` looks up in the *current turn's* `ctx` models, which may have been built from the old snapshot.

Thus crew default changes and new model aliases are not "immediately recognized" by already-running sessions.

## Desired behavior

- After a file under `config/crew/`, `config/models/`, or `config/providers/` changes (and hot-reload processes it), a subsequent turn in an *existing* open session for the affected crew should resolve using the new crew default and see the new model alias in the catalog (no process restart required).
- `/crew <name>` followed by a normal turn (or `/status`) should pick up the updated crew's `:model`.
- `/model <alias>` should succeed for aliases present in the (reloaded) catalog.
- The hot-reload contract ("edit config, it takes effect") should hold for session-visible data (crew defaults, model list) the same way it does for hooks, comms, etc.

## Acceptance criteria

- Change `config/crew/<foo>.edn` `:model` (or add a new model file) while a session for `<foo>` is active and hot-reload is on → next turn (after watcher event) resolves the new model; `/status` and crew list for the session reflect it.
- `/model <new-alias>` works from within the running session once the change is loaded.
- `/crew <name>` + a follow-up turn picks up the crew's current default from the reloaded snapshot.
- No regression in existing hot-reload tests (hooks, comms, server berths, etc.).
- Boot and fresh CLI paths remain unchanged and correct.
- Measurable: a turn after a crew/model file change does not require the host process to have been restarted.

## Notes / Open questions

- Is the watcher/reloader only wired in the full server path (`isaac-server/app`)? Do ACP / prompt-loop / dedicated-crew clients get a config change source?
- Should `resolve-behavior` / charge building always force a fresh `load-config-result` for crew/model data on hot-reload events, or rely solely on the updated snapshot?
- Related prior work: m14k (per-turn config reload noise), be94 (hooks hot-reload), various session/crew resolution beans.
- The concrete repro used the chatgpt provider + gpt-5.4 model + marvin crew on zanebot.

## Handoff

Capture the exact reload path that should cause `set-snapshot!` to make new `:crew` / `:models` visible to `session-ctx/resolve-crew-context` and charge `:models`.

Bean created for tracking the fix.
