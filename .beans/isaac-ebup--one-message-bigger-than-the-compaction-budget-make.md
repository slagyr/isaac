---
# isaac-ebup
title: One message bigger than the compaction budget makes a session unrecoverable, and there is no repair path
status: in-progress
type: bug
priority: critical
created_at: 2026-09-20T19:52:47Z
updated_at: 2026-10-21T18:38:22Z
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


## Re-scope (2026-10-21, Micah + planner)

State on main (isaac-agent 21c46b7): nothing of this bean is done.
`compaction.clj` `chunk-plan` still returns `:oversized-single` with no
chunks, and nothing caps an entry on write. Decisions (Micah, 2026-10-21):

- **Stub, not excerpt.** When an entry is larger than the window, the summary
  request carries a one-line stub in its place:
  `[entry omitted: <tokens> tokens, larger than the <window>-token window]`.
  The entry itself stays in the transcript; only the summary request changes.
  The rest of the history compacts normally.
- **Cap on write.** Every transcript entry is capped at
  `defaults.transcript.max-bytes` (default 262144), head and tail kept with
  the existing `isaac.agent.tool.output-cap/cap-result` marker ("bytes
  truncated; byte cap hit"), and a `:session/entry-truncated` warn naming the
  session and role. No side file with the full payload.
- **No `sessions prune` command.** With the stub, the repair path for a session
  already blocked is `isaac sessions unset <id>.block`; the next turn compacts.

Where it likely goes: the stub in `isaac.agent.session.compaction`
(chunk planning / the messages handed to `summarize-messages`); the cap at the
store append path (`isaac.agent.session.store/*`, `transcript.clj`), so every
store implementation gets it. Register `defaults.transcript.max-bytes` in the
config schema beside `defaults.tools.max-bytes`.

## Acceptance

- [ ] `cd isaac-agent && bb features features/session/compaction_overflow.feature:133` (an entry bigger than the window is left out of the summary and the rest compacts)
- [ ] `cd isaac-agent && bb features features/session/compaction_overflow.feature:171` (a reply bigger than the transcript cap is stored as its head and tail)
- [ ] `@wip` removed from both; `bb features features/session/compaction_overflow.feature` green

feature-baseline: isaac-agent 21c46b718a744bfd0100f553a958053e02ad5585
feature-blob: isaac-agent features/session/compaction_overflow.feature 970fb9eaababeb904cf6482b1ee0dce308575356 133,171
