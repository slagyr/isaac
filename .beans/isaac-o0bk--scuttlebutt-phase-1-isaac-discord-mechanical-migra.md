---
# isaac-o0bk
title: 'Scuttlebutt phase 1: isaac-discord mechanical migration to the new Comm protocol'
status: todo
type: task
priority: normal
tags:
    - scuttlebutt
    - discord
created_at: 2026-09-03T16:40:55Z
updated_at: 2026-09-03T17:44:40Z
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
