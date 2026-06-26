---
# isaac-opc4
title: Ambiguous gherclj step "config:" collides across isaac-agent and isaac-server
status: completed
type: bug
priority: high
tags: []
created_at: 2026-06-26T21:45:52Z
updated_at: 2026-06-26T22:16:12Z
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

## Implementation (work-3)
- **Foundation** (`eb3bcb0`): canonical `config:` in `isaac.foundation.harness-config-steps` (spec + spec-support).
- **Server** (`96bd83a`): renamed to `server config:` → `server-config-applied`; disk persist delegates to foundation; bumped foundation-spec pin; fixed dev-reload log row order.
- **Agent** (`75190ff`): dropped duplicate `config:`; delegates to foundation; foundation SHA `eb3bcb0`.
- **Cron** (`d6440c3`): resolves foundation `config-applied`.
- **Verification**: isaac-hail features 80/0 (dev-local); isaac-server features 47/0 (git deps).

## Verification

Verified on fetched GitHub `main`:

- `isaac-foundation` `eb3bcb0785c4e86d9e8c032c26aa7b4341b9f04d`
- `isaac-agent` `75190ffa45edc19e87402b60cc4bce7277ca46d8`
- `isaac-server` `96bd83ad315a7a9c2dbd840d3089ae6d6c9153cc`
- `isaac-cron` `d6440c30a3213a7822c518126fc669df97e97ea4`
- `isaac-hail` `da07bd8878c39f94e79412b225f38b2567171245`

Proofs were green:

- `isaac-hail` `bb features` in a sibling worktree layout matching its `../isaac-*` local-root feature alias -> `80 examples, 0 failures, 333 assertions, 2 pre-existing pending`
- `isaac-server` `bb features` -> `47 examples, 0 failures, 120 assertions`

The original `config:` ambiguity is resolved on current heads. The first bare
hail worktree run failed only because its `:features` alias resolves
`../isaac-agent`, `../isaac-server`, and `../isaac-foundation`; once verified
in that intended sibling layout, the suite was green.
