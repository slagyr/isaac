---
# isaac-o0bk
title: 'Scuttlebutt phase 1: isaac-discord mechanical migration to the new Comm protocol'
status: in-progress
type: task
priority: normal
tags:
    - scuttlebutt
    - discord
created_at: 2026-09-03T16:40:55Z
updated_at: 2026-09-04T03:16:16Z
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
