---
# isaac-zbg8
title: 'Scuttlebutt phase 1: isaac-acp mechanical migration to the new Comm protocol'
status: in-progress
type: task
priority: normal
tags:
    - acp
    - unverified
    - scuttlebutt
created_at: 2026-09-03T16:40:55Z
updated_at: 2026-09-04T03:35:07Z
blocked_by:
    - isaac-jarr
---

Split from isaac-i5ps so the pin bump can ride the scuttlebutt train without the rendering redesign. Mechanical only: extend+defaults; on-chatter → agent_message_chunk exactly as today's on-text-chunk; on-reply → same final message path; tool events unchanged; compaction on-bulletin → today's session/update compaction notification (isaac-5qc scenario migrates to bulletin rows). No agent_thought_chunk yet — reckoning/aside rendering (option B, 🧠/💬) stays in isaac-i5ps, which becomes blocked by this bean. Bump agent pin to the released 5nxf SHA; features/comm/acp green.



## Planning (2026-09-03) — no new scenarios; the existing suite IS the contract

How ACP gets reply text today: only via on-text-chunk. The agent synthesizes chunk events even for non-streaming providers (drive/turn.clj content→chunks path, kept on the 5nxf branch as on-chatter), and the server's success branch after dispatch emits nothing. So under the new protocol:

- `on-chatter` → `agent_message_chunk` (today's on-text-chunk body, incl. normalize-text-chunk).
- `on-reply`, `on-aside`, `on-reckoning`, `on-cycle-*` → **defaults (noop)**. on-reply always fires with the full text; emitting it would double-render every reply on the same destination. Reckoning/thought rendering is isaac-i5ps.
- `on-bulletin` → today's four compaction thought-notifications keyed on `:kind` (`:compaction/start` "compacting...", `/success` "compacted.", `/failure`, `/disabled`); other kinds nil. Byte-identical client output.
- tool-call / tool-cancel / tool-result unchanged; `on-tool-progress` default.
- `send!` unchanged.
- Shape: `(deftype AcpComm [output-writer])` + `(extend AcpComm comm/Comm (merge comm/defaults {...}))` — never inline.

Guards already in the suite (no absence tests needed — [[no-absence-tests]]):
- `prompt.feature` non-streaming text → exactly one `agent_message_chunk` (the step's no-trailing-listed-notifications check makes the row count exact) = the no-double-render contract for on-reply.
- `streaming.feature` chunk forwarding = on-chatter contract.
- `compaction_notification.feature` `agent_thought_chunk | compacting...` = the bulletin contract; row text unchanged.
- tool_call / cancel / error features unchanged.

## Step ledger

| step | status |
|------|--------|
| all | reuse — no feature rows change in this bean |

## Acceptance

    cd isaac-acp
    # deps.edn + bb.edn agent pin = the SHA isaac-jarr releases
    bb features     # 64/0 (cancellation.feature flake is isaac-2bni; rerun once)
    bb spec
Then isaac-i5ps (rendering redesign) unblocks.



## Pin target updated (2026-09-03, planner)

Pin the agent at **`bf4323326c150bdcda4be2c0245cf2f7b0cbd629`** (Release 0.1.43) — not jarr's 0b52823. Main advanced past jarr with isaac-vuto (token accounting) and isaac-q34y (idle sealing), both verified and merged; the full gate on merged main is 756/0 features, 1612/0 spec. Same protocol as 0.1.42 for this bean's purposes.

## Worker Notes (2026-09-04)

- Implemented the mechanical phase-1 ACP migration in sibling worktree `isaac-acp-zbg8` on branch `bean/isaac-zbg8`.
- Replaced inline `deftype` protocol methods with state-only `(deftype AcpComm [output-writer])` plus `(extend AcpComm comm/Comm (merge comm/defaults {...}))`.
- Mapped `on-chatter` to the existing `agent_message_chunk` rendering path and `on-bulletin` to the existing compaction thought notifications for `:compaction/start`, `:compaction/success`, `:compaction/failure`, and `:compaction/disabled`.
- Left `on-reply`, `on-aside`, `on-reckoning`, `on-cycle-*`, and `on-tool-progress` at protocol defaults to preserve the no-double-render contract for ACP phase 1.
- Bumped `deps.edn` and `bb.edn` agent / agent-spec pins to `bf4323326c150bdcda4be2c0245cf2f7b0cbd629`.
- Updated focused specs to drive the new entry points (`on-chatter`, `on-bulletin`) without changing feature rows.
- Verified:
  - `bb features features/comm/acp` → `64 examples, 0 failures, 151 assertions`
  - `bb features` → `64 examples, 0 failures, 151 assertions`
  - `bb spec` → `70 examples, 0 failures, 195 assertions`
- branch: `bean/isaac-zbg8` @ `3be2975` (base `origin/main@ef03bbe`)
