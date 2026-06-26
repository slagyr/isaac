---
# isaac-opc4
title: Ambiguous gherclj step "config:" collides across isaac-agent and isaac-server
status: todo
type: bug
priority: high
created_at: 2026-06-26T21:45:52Z
updated_at: 2026-06-26T21:46:03Z
blocking:
    - isaac-c58s
---

The hail feature suite is BROKEN on main: `clojure -M:features` in isaac-hail dies with

    Execution error at gherclj.core/classify-step
    ambiguous step match: "config:" matches: config-applied, configure

## Root cause
Two modules each register the exact gherclj step `config:`:
- isaac-agent `spec/isaac/session/session_steps.clj` -> `config-applied`: persists every dotted key into on-disk `config/isaac.edn` (special-cases `log.output` -> in-memory logger; ignores `bind-server-port`).
- isaac-server `spec/isaac/server/server_steps.clj:607` -> `configure`: writes `:server-config` in harness state + `persist-config-entry!` (special-cases `log.output`, `bind-server-port`).

Any harness that loads BOTH step libs gets two defs for `config:`. isaac-hail's `feature-steps/isaac/hail_steps.clj` requires `isaac.server.server-steps` (for its helpers) AND pulls in agent session-steps, so both `config:` defs register. gherclj refuses the ambiguous match and the whole hail suite fails to build.

The two impls overlap heavily (both handle `log.output`, both persist dotted config) — they look like near-duplicate steps that drifted apart in the monolith carve.

## How it surfaced
Latent until isaac-3wic added `features/hail-naming.feature` (commit 41a3cfd), the first hail feature to USE `Given config:` (to set `hail-settings.naming-strategy`). Before that the two defs were both loaded but never matched, so the ambiguity never triggered.

## Impact
- Entire isaac-hail feature suite cannot build/run.
- Blocks verification of isaac-c58s (hail selector conforming) — its new @wip scenarios are written but unrunnable.
- Any harness loading both agent+server steps is at risk.

## Fix (do NOT add a third differently-named step as a dodge)
Resolve the collision so `config:` is unambiguous and features keep using the proper `config:`:
- Decide the single canonical `config:` (the on-disk-persisting one is what `hail-naming` needs — it shells out to `hail send` which reads `config/isaac.edn`), and rename the other to a proper distinct name (e.g. server-scoped `server config:`), updating its usages.
- 6 isaac-server features use `Given config:`; isaac-imessage calls `server-steps/configure` directly (fn, not via step text — unaffected by a step-text rename).
- Consider consolidating the two near-duplicate impls rather than just renaming, since they overlap.

## Repro
cd isaac-hail && clojure -M:features  (or bb features)
