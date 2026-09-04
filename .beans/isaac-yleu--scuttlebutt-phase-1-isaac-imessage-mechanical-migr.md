---
# isaac-yleu
title: 'Scuttlebutt phase 1: isaac-imessage mechanical migration to the new Comm protocol'
status: in-progress
type: task
priority: normal
tags:
    - scuttlebutt
    - imessage
    - unverified
created_at: 2026-09-03T16:40:56Z
updated_at: 2026-09-04T03:56:14Z
blocked_by:
    - isaac-jarr
---

Per the iMessage review (isaac-frvu, decided 2026-08-31): pure delivery. Replace the eight no-op turn methods with comm/defaults via extend; send! is the entire implementation; replies keep riding the outbound delivery queue (result->reply-text → chunker moves under on-reply only if the delivery path needs it — otherwise defaults). Break-glass alerts remain the attention system's job (isaac-vrtb), never the comm. Bump agent pin to the released 5nxf SHA; module bb features + bb spec green.



## Planning (2026-09-03) — no new scenarios

Today `ImessageComm` no-ops all ten turn methods; replies ride the outbound delivery queue (reply.feature: turn result → delivery record → worker → AppleScript runner), never on-turn-end. Under the new protocol the deftype keeps its fields (`[host state*]`) and takes `comm/defaults` for every turn event; `send!` is the whole extend map. The full existing suite (intake, intake_filtering, lifecycle, reply, routing, send, turn_context, watch_hydrate) is the contract and must stay green against the new pin. Break-glass stays with attention (isaac-vrtb).

## Step ledger

| step | status |
|------|--------|
| all | reuse — no feature rows change in this bean |

## Acceptance

    cd isaac-imessage
    # deps.edn + bb.edn agent pin = the SHA isaac-jarr releases
    bb features
    bb spec
Shape: `(extend ImessageComm comm/Comm (merge comm/defaults {:send! ...}))` — never inline.



## Pin target updated (2026-09-03, planner)

Pin the agent at **`bf4323326c150bdcda4be2c0245cf2f7b0cbd629`** (Release 0.1.43) — not jarr's 0b52823. Main advanced past jarr with isaac-vuto (token accounting) and isaac-q34y (idle sealing), both verified and merged; the full gate on merged main is 756/0 features, 1612/0 spec. Same protocol as 0.1.42 for this bean's purposes.

## Worker Notes (2026-09-04)

- Implemented the mechanical phase-1 iMessage migration in sibling worktree `isaac-imessage-yleu` on branch `bean/isaac-yleu`.
- Replaced the inline no-op Comm methods with state-only `(deftype ImessageComm [host state*])` plus `(extend ImessageComm comm/Comm (merge comm/defaults {:send! ...}))`.
- Kept `send!` as the complete Comm implementation; replies continue to ride the outbound delivery queue and no turn-event rendering was added to the comm.
- Bumped `deps.edn` and `bb.edn` agent / agent-spec / telly pins to `bf4323326c150bdcda4be2c0245cf2f7b0cbd629`.
- Verified:
  - `bb features` → `15 examples, 0 failures, 30 assertions`
  - `bb spec` → `41 examples, 0 failures, 69 assertions`
- Observed existing suite output noise during green runs (logger lines and feature boot banner prints), but acceptance passed and no additional product changes were introduced in this bean.
- branch: `bean/isaac-yleu` @ `b4ac3fe` (base `origin/main@c067dd3`)
