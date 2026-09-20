---
# isaac-ebup
title: One message bigger than the compaction budget makes a session unrecoverable, and there is no repair path
status: todo
type: bug
priority: critical
created_at: 2026-09-20T19:52:47Z
updated_at: 2026-09-20T19:52:47Z
---

A session dies permanently the moment one entry is bigger than the compaction
budget, and nothing in the system can bring it back.

Proved on zanebot 2026-09-20 19:50Z. A probe hail at `isaac-work-2` with the
gauge fix deployed (isaac-166j), so compaction finally fires:

    :session/compaction-started
    :session/compaction-chunk-plan   :chunk-count 0
      :failure {:reason :oversized-single
                :compactable {:id "b3396f81" :role "assistant"
                              :content-chars 2235805 :tokens 511568}}
      :budget 200000
    :session/compaction-chunk-infeasible
    :session/compaction-failed :error :llm-error
    :drive/context-exhausted  ->  turn ended :context-exhausted
    :hail/delivery-deferred   (deferred, not dead-lettered — that part is right)

A chunk cannot be smaller than one message, so a 511k-token message can never
be summarized under a 200k budget. The session is stuck at 590,320 tokens
against a 200,000 window forever.

## Where the giant entries came from

Both poisoned sessions carry exactly one, and both are raw claude-code
stream-json stored as the assistant's *content* —
`{"type":"system","subtype":"init",...}` then thousands of `stream_event`
lines:

| session | entry | chars | tokens | share of session |
| --- | --- | --- | --- | --- |
| isaac-work-2 | `b3396f81` | 2,235,805 | 511,568 | 86% |
| isaac-work-3 | `81a86645` | 1,855,440 | 420,952 | 83% |

Timestamps 05:21:21 and 05:23:37 — the window of the fence-fallback bug
(isaac-zz6d, fixed in claude-code 0.1.16). When the drive lost the parse it
wrote the entire stream in as the reply. One bad write, and the session is
unrecoverable.

## Work

- **Cap what is stored.** No single message should be written at more than a
  small fraction of the context window. Truncate, keep the head and tail, and
  leave a pointer to the full payload on disk. A parse failure must never be
  able to write megabytes into a transcript.
- **Compaction must survive an oversized single.** `:oversized-single` is
  currently fatal: the plan yields zero chunks and the turn ends
  `:context-exhausted`. Truncate or quarantine the offending entry (replacing
  it with a stub that keeps the id/parent chain) and compact the rest.
- **Give operators a repair path.** Today the only fixes are archiving the
  transcript or hand-editing it, and hand-editing is — rightly — refused as
  transcript tampering. Something like `isaac sessions prune <id> --entry <eid>`
  or `--oversized`, which rewrites the entry to a stub through the store's own
  API, with the original kept alongside.

## Scenarios

A transcript whose largest entry exceeds the chunk budget still compacts: the
oversized entry becomes a stub and the remaining history is summarized. A
provider response beyond the storage cap is truncated on write, with the full
payload written beside the transcript and named in the stub.
