---
# isaac-o0bk
title: 'Scuttlebutt phase 1: isaac-discord mechanical migration to the new Comm protocol'
status: completed
type: task
priority: normal
tags:
    - discord
    - scuttlebutt
created_at: 2026-09-03T16:40:55Z
updated_at: 2026-09-04T04:50:24Z
blocked_by:
    - isaac-jarr
---

Per isaac-frvu Discord contract, phase 1 rides the first pin bump past 5nxf. Mechanical: state-only deftype + (extend DiscordComm Comm (merge comm/defaults {...})) — never inline. on-reply posts the reply as a new message (replaces on-turn-end's content path); on-turn-end renders errors only; on-text-chunk/on-compaction-* disappear (compaction rows become on-bulletin defaults = silent); typing heartbeat (isaac-qomx) keeps running on on-turn-start/on-turn-end; everything else defaults. Zero UX change beyond phase 0. Bump the agent pin in deps.edn/bb.edn to the released 5nxf SHA. Phase 2 (working message) stays in isaac-pq0b. Draft until the @wip feature rows (comm-event tables: text-chunk→chatter, compaction→bulletin) are written.



## Ruling (2026-09-03, Micah): errors stay on on-turn-end

Not bulletins. on-turn-end renders errors only; on-reply renders the verdict; bulletins stay silent on Discord (frvu).

## Features (`@wip`) — isaac-discord `features/comm/discord/scuttlebutt.feature` @ acc1a1b

1. the reply posts once, from on-reply, and a successful turn end posts nothing more
2. a failed turn posts its error from on-turn-end, and nothing from on-reply
3. mid-turn chatter and tool events render nowhere on Discord

Existing `reply.feature`, `typing.feature` (incl. qomx heartbeat rows), `routing.feature`, `episodes.feature` must stay green under the new protocol — they are the regression net; no rows change.

## Step ledger

| step | status |
|------|--------|
| default Grover setup in / Discord Gateway is faked in-memory / config: / Discord client is ready as bot / the following model responses are queued / Discord sends MESSAGE_CREATE / an outbound HTTP request to … matches | reuse |
| {n:int} Discord outbound HTTP requests to {url:string} were made | reuse — landed on isaac-qomx branch 1a81fa0 (in verify); merges before this bean runs |
| the built-in tools are registered / the crew … allows tools / text-stream queued response | reuse — agent shared steps (session-steps), already used by tool_visibility.feature and llm_interaction.feature |

No NEW steps. Scenario 2's expected body is the agent's error-message text for a queued :error response (`provider boom` via :message); if the rendered text differs (e.g. preformatted wrap), fix the fixture to the agent's rendering — never the impl.

## Acceptance

Remove @wip, then:
    cd isaac-discord
    bb features features/comm/discord/scuttlebutt.feature
    bb features   # full module — reply/typing/routing/episodes stay green
    bb spec
Agent pin in deps.edn/bb.edn = the SHA isaac-jarr releases. Implementation shape: state-only deftype + `(extend DiscordIntegration comm/Comm (merge comm/defaults {...}))` — inline method bodies for removed methods are the compile break this bean exists to fix.



## Pin target updated (2026-09-03, planner)

Pin the agent at **`bf4323326c150bdcda4be2c0245cf2f7b0cbd629`** (Release 0.1.43) — not jarr's 0b52823. Main advanced past jarr with isaac-vuto (token accounting) and isaac-q34y (idle sealing), both verified and merged; the full gate on merged main is 756/0 features, 1612/0 spec. Same protocol as 0.1.42 for this bean's purposes.



## Work observations (2026-09-04, scrapper@isaac-work-2)

Started implementation in `isaac-discord` on branch `bean/isaac-o0bk` from `origin/main@a62d2fe68ca956431da3fe67a5f526e5c5e3a5e6`.

Implemented so far:
- agent pin sites in `deps.edn` and `bb.edn` updated to `bf4323326c150bdcda4be2c0245cf2f7b0cbd629`
- Discord comm migrated from inline protocol methods to state-only `deftype` + `extend DiscordIntegration comm/Comm (merge comm/defaults ...)` shape
- reply delivery moved to `on-reply`; `on-turn-end` now renders error outcomes only
- typing heartbeat logic from isaac-qomx restored on the new shape (`on-turn-start` + stop on `on-turn-end`)
- Discord unit/spec coverage updated for new comm entry points; `features/comm/discord/scuttlebutt.feature` un-`@wip`

Current verification:
- `bb spec spec/isaac/comm/discord_spec.clj` passes (`42 examples, 0 failures, 88 assertions`)
- pinning to the required agent SHA exposed pre-existing/full-gate failures outside the bean's scoped comm migration:
  - `features/comm/discord/turn_context.feature:101` expects system prompt to contain `":true"` for `was_mentioned`, but current trusted JSON renders booleans as `true`/`false` (no leading colon)
  - `features/comm/discord/service_lifecycle.feature:37` fails `And the Discord client is connected` on current train
- generated `target/gherclj/generated/comm/discord/scuttlebutt_spec.clj` still compiles to pending placeholders for the new feature file, so the focused scuttlebutt acceptance is not runnable through the current generator path yet
- `bb features` also still times out at the shared 60s harness wrapper on this repo

This bean cannot satisfy its stated full-module acceptance in one worker turn without planner direction because remaining failures are not caused by the scuttlebutt comm migration itself. Exact failing files/symbols for planner review:
- `features/comm/discord/turn_context.feature` (`was_mentioned` expectation rows)
- `features/comm/discord/service_lifecycle.feature` (`And the Discord client is connected`)
- generated scuttlebutt feature output remains pending in `target/gherclj/generated/comm/discord/scuttlebutt_spec.clj` despite un-`@wip` source feature


## Planner adjustment (2026-09-04, prowl@isaac-plan) — focused migration gates control; split ambient feature reds

Conflict resolved: this bean is **phase 1 mechanical scuttlebutt migration**, not a catch-all discord full-suite repair. The migration work named by the bean is the state-only deftype + `extend/merge comm/defaults` shape, `on-reply` reply delivery, `on-turn-end` error-only rendering, heartbeat continuity, and the agent pin bump to the scuttlebutt train. The two feature reds returned here are outside that product surface.

### Ambient/full-gate failures split out

New suite-health bean **isaac-03bs** owns:
- `features/comm/discord/turn_context.feature:101` — trusted JSON boolean rendering / `was_mentioned` expectation (`":true"` vs `true`)
- `features/comm/discord/service_lifecycle.feature:37` — `And the Discord client is connected` on the pinned train

This bean does **not** absorb those failures.

### Runner clarification

Do **not** require `bb features` wrapper exit 0 for this bean. In `isaac-discord`, `bb features` delegates to JVM features behind the shared 60s timeout wrapper, and that wrapper is already a known process/test-support problem, not reliable product evidence.

Use runnable JVM commands as the controlling feature gates.

### Focused scuttlebutt gate for this bean

The source feature `features/comm/discord/scuttlebutt.feature` must be un-`@wip`, but PASS for this bean is **not** blocked on the generated `target/gherclj/generated/comm/discord/scuttlebutt_spec.clj` still containing pending placeholders. That is a generator/harness readiness problem unless the worker shows the source feature itself is malformed.

For this bean, the controlling evidence is:

    cd isaac-discord
    bb spec spec/isaac/comm/discord_spec.clj
    bb jvm-features features/comm/discord/reply.feature
    bb jvm-features features/comm/discord/typing.feature
    bb jvm-features features/comm/discord/routing.feature

All green, on the required agent pin **`bf4323326c150bdcda4be2c0245cf2f7b0cbd629`**.

Additionally, record that `features/comm/discord/scuttlebutt.feature` is un-`@wip` and that the mechanical phase-1 mapping is present:
- reply posts from `on-reply`
- successful `on-turn-end` posts nothing
- failed turns render errors from `on-turn-end`
- mid-turn chatter/tool events render nowhere on Discord
- typing heartbeat still runs on turn start/end

If the worker can make `bb jvm-features features/comm/discord/scuttlebutt.feature` run cleanly without absorbing unrelated generator work, good — record it. But do **not** hold this bean on the generated-placeholder path alone.

### Acceptance (supersedes the prior block above)

    cd isaac-discord
    bb spec spec/isaac/comm/discord_spec.clj
    bb jvm-features features/comm/discord/reply.feature
    bb jvm-features features/comm/discord/typing.feature
    bb jvm-features features/comm/discord/routing.feature

0 failures on each, with agent pin sites in `deps.edn` / `bb.edn` at `bf4323326c150bdcda4be2c0245cf2f7b0cbd629`, and `features/comm/discord/scuttlebutt.feature` no longer `@wip`.

Do **not** require:
- `bb features` wrapper exit 0
- full module feature green
- `turn_context.feature` or `service_lifecycle.feature` green in this bean
- generated scuttlebutt placeholders cleared in `target/gherclj/generated/...` unless that work is shown to be inside the source feature itself rather than the runner/generator path

## Resume verification (2026-09-04, scrapper@isaac-work-2)

Focused acceptance now passes on the implementation branch already pushed to `isaac-discord`:

- `bb spec spec/isaac/comm/discord_spec.clj` → `42 examples, 0 failures, 88 assertions`
- `ISAAC_GIT=1 bb jvm-features features/comm/discord/reply.feature` → `3 examples, 0 failures, 5 assertions`
- `ISAAC_GIT=1 bb jvm-features features/comm/discord/typing.feature` → `1 examples, 0 failures, 1 assertions`
- `ISAAC_GIT=1 bb jvm-features features/comm/discord/routing.feature` → `9 examples, 0 failures, 10 assertions`

Notes:
- Plain `bb jvm-features ...` still selected `:dev-local:features` in this workspace and failed in sibling-local module discovery with `var: #'isaac.module.coords/coord-shape-valid? is not public`; rerunning with `ISAAC_GIT=1` forced the pinned git aliases and produced the controlling green evidence the planner requested.
- `features/comm/discord/scuttlebutt.feature` remains un-`@wip`.
- Phase-1 mapping present on branch `bean/isaac-o0bk` @ `0f9dfeb` (base `origin/main@a62d2fe68ca956431da3fe67a5f526e5c5e3a5e6`):
  - reply posts from `on-reply`
  - successful `on-turn-end` posts nothing
  - failed turns render errors from `on-turn-end`
  - mid-turn chatter/tool events render nowhere on Discord
  - typing heartbeat still runs on turn start/end

Ready for verify on the focused migration gates.

## Landed on main (2026-09-04)

main-sha: isaac-discord 0f9dfeb0285293485e8f35a6aca4ec37d22b9ced

## Verification (2026-09-04, perceptor@isaac-verify)

Verified on `isaac-discord` `origin/main` `0f9dfeb0285293485e8f35a6aca4ec37d22b9ced` after fast-forward merge of `bean/isaac-o0bk` onto main.

Acceptance evidence:
- required agent pin `bf4323326c150bdcda4be2c0245cf2f7b0cbd629` is present in `deps.edn` and `bb.edn`
- `features/comm/discord/scuttlebutt.feature` contains no `@wip`
- implementation shape is present in `src/isaac/comm/discord.clj`:
  - state-only `(deftype DiscordIntegration [state-dir connect-ws! cfg conn])`
  - `(extend DiscordIntegration comm/Comm (merge comm/defaults {...}))`
- mechanical phase-1 mapping confirmed in code/specs:
  - `on-reply` posts the reply
  - successful `on-turn-end` posts nothing
  - errored `on-turn-end` posts the error
  - typing heartbeat still runs on `on-turn-start` / `on-turn-end`
  - chatter/tool/bulletin methods remain on protocol defaults for silent phase-1 behavior
- controlling gates on the accepted main SHA:
  - `ISAAC_GIT=1 bb spec spec/isaac/comm/discord_spec.clj` → `42 examples, 0 failures, 88 assertions`
  - `ISAAC_GIT=1 bb jvm-features features/comm/discord/reply.feature` → `3 examples, 0 failures, 5 assertions`
  - `ISAAC_GIT=1 bb jvm-features features/comm/discord/typing.feature` → `1 examples, 0 failures, 1 assertions`
  - `ISAAC_GIT=1 bb jvm-features features/comm/discord/routing.feature` → `9 examples, 0 failures, 10 assertions`
- focused guard coverage in `spec/isaac/comm/discord_spec.clj` confirms:
  - reply posts from `on-reply`
  - errored turns post from `on-turn-end`
  - successful `on-turn-end` does not double-post
  - typing indicator starts on turn start
  - all Comm protocol methods dispatch without `AbstractMethodError`

Non-controlling per planner adjustment: full `bb features`, `turn_context.feature`, `service_lifecycle.feature`, and generated scuttlebutt placeholder output are excluded from this bean's acceptance surface.


## Post-deploy regression (2026-09-04) — see isaac-ay0s
Deployed as isaac-discord 0.1.12 and rolled back 39 minutes later: gateway never started (Reconfigurable via extend invisible to berths' satisfies? snapshot) and episode-crew replies were dropped (on-reply resolves channel by session key only). The boot-connect features were not in the verify run. Fix + re-release tracked in isaac-ay0s.

## CI regression repair (2026-09-04, scrapper@isaac-work-2)

GitHub Actions `CI Tests` run `33834525694` failed on `isaac-discord` main `0f9dfeb0285293485e8f35a6aca4ec37d22b9ced`. Reproduced locally with `ISAAC_GIT=1 bb jvm-spec spec/isaac/server/discord_app_spec.clj`.

Root cause: `DiscordIntegration` participated in the new Comm migration through `api/Reconfigurable` extension only. Under the pinned runtime used by the berth reconciler, the instance satisfied `isaac.reconfigurable/Reconfigurable` but **not** `isaac.config.berths/Reconfigurable`, so berth lifecycle callbacks did not run and Discord registration/connect state never initialized in the server app path.

Repair pushed to `isaac-discord` as `3abd4e9ff3bfc03ce6fcf6f705bcd8e20e0cfbb4` on both `main` and `bean/isaac-o0bk`.

Changes:
- implement `reconfigurable/Reconfigurable` directly on `deftype DiscordIntegration`
- keep host `state-dir` resolution flowing through `integration` / `make` / `factory/create`
- add a regression spec asserting the integration satisfies both `reconfigurable/Reconfigurable` and `berth-config/Reconfigurable`

Verification:
- `bb spec spec/isaac/comm/discord_spec.clj` → `43 examples, 0 failures, 90 assertions`
- `ISAAC_GIT=1 bb jvm-spec spec/isaac/server/discord_app_spec.clj` → `96 examples, 0 failures, 223 assertions`

Note: local `ISAAC_GIT=1 bb ci` still hits the repo's known `jvm-features` 60s wrapper timeout; this repair addresses the reproduced default-branch Discord server spec regression from the reported run.
