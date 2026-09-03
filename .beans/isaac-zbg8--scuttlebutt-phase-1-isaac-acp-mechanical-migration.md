---
# isaac-zbg8
title: 'Scuttlebutt phase 1: isaac-acp mechanical migration to the new Comm protocol'
status: todo
type: task
priority: normal
tags:
    - scuttlebutt
    - acp
created_at: 2026-09-03T16:40:55Z
updated_at: 2026-09-03T17:47:18Z
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
