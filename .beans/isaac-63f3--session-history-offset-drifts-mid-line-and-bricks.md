---
# isaac-63f3
title: Session history offset drifts mid-line and bricks the session (JsonParseException)
status: completed
type: bug
priority: high
created_at: 2026-07-05T16:16:48Z
updated_at: 2026-07-06T13:40:07Z
---

## Problem

A session's `:effective-history-offset` is a raw BYTE offset that can land in the middle of a transcript line. When the prompt is built, `read-transcript-from-offset` (isaac.session.store.impl-common) reads from that byte to EOF and `split-lines` + JSON-parses each line — so the first "line" is a partial fragment and throws `com.fasterxml.jackson.core.JsonParseException`. Every turn on that session then crashes; deliveries dead-letter; the bean is stuck.

## Evidence (2026-07-05, zanebot, isaac-work-1)

- Effective offset was 9273989, but the nearest line boundary is 9273657 (a `{"type":"compaction",...}` entry start) — the offset had drifted 332 bytes into a `"...reported a CI regression..."` message entry.
- Error: `Unrecognized token 'reported': line 1, column 9` — column 9 of the partial first fragment.
- Every 4tn1 delivery to work-1 threw this and dead-lettered (loop re-dispatched forever). Model-agnostic (opus failed too) — it is in transcript reading, not the provider.
- Manual fix: snapped the offset to 9273657 (line boundary) -> crash gone, all history preserved.

## Root cause

Compaction computes the offset via `transcript-byte-offset`, which RE-SERIALIZES entries with `write-json` and sums bytes. That serialization is not guaranteed byte-identical to what was originally appended (key ordering, unicode/`/` escaping, whitespace), so the stored offset can be off by N bytes and fall mid-line.

## Desired behavior (any/all)

- `read-transcript-from-offset` must snap the offset to the next newline before splitting/parsing (defensive: never parse a partial line). Simplest, highest-value fix.
- AND/OR store the effective-history boundary as an entry id / line index, not a raw byte offset, so it can't drift.
- AND/OR compaction should derive the offset from the actual on-disk bytes (e.g., sum lengths of the exact appended lines), not a re-serialization.

## Scope

isaac-agent: `src/isaac/session/store/impl_common.clj` (read-transcript-from-offset, transcript-byte-offset), compaction offset computation (session/compaction or drive/turn splice).

## Acceptance (gherkin, isaac-agent)

- Given a session whose effective-history-offset falls mid-line, when a turn builds the prompt, then it reads from the next line boundary and does NOT throw (no partial-line parse).
- Given compaction runs, then the stored offset lands exactly on an entry boundary (round-trip: read-from-offset parses cleanly).

Priority: HIGH — a drifted offset silently bricks a session permanently.


---

## Resolution (unverified — for verifier)

Implemented in isaac-agent `main` commits **1625017** (mid-line + write-side) and
**7929dc6** (merged isaac-0h7b orphan tool-result), all in
`src/isaac/session/store/impl_common.clj`:

**Read-side snap (isaac-63f3).** `read-transcript-from-offset` snaps the offset
back to the start of the line it lands in (`snap-to-line-start`) before
split/parsing — never parses a partial JSON line (JsonParseException).

**Write-side correctness (isaac-63f3).** `splice-compaction!` stores
`:effective-history-offset` over the actual written prefix (up to the compaction
entry), not a re-serialization of raw `before`.

**Orphan tool-result drop (isaac-0h7b, merged).** After line snap + parse,
`drop-orphan-toolresults` removes any `toolResult` whose `toolCall` is not in the
active slice — fixes codex Responses `function_call_output` without matching call.

**Tests:**
- `spec/isaac/session/store/impl_common_spec.clj` — mid-line snap; orphan drop;
  paired preserve (offset 0).
- `index_impl_spec.clj` — compaction offset lands on line boundary + round-trip.
- `features/session/compaction_logging.feature` — two new scenarios (file store +
  `effective history starts after transcript entry index` step): orphan drop at
  split boundary; paired toolCall/toolResult preserved. Existing "Compaction keeps
  toolCall and toolResult together" still covers compaction splice regression.

**Verification:** isaac-agent `bb verify` — config-bypass-lint ok; **1165 spec
examples / 2281 assertions, 0 failures**; **580 feature examples / 1295
assertions, 0 failures**; `bb lint` src clean.


## Merged: isaac-0h7b (orphaned tool-result) — same root code path

Both bugs are `read-transcript-from-offset` (isaac.session.store.impl-common) producing an INVALID effective head from a raw byte offset. Two failure modes:

1. **Mid-line offset** → partial first line → `JsonParseException` (crashes every turn; model-agnostic). [original 63f3]
2. **Tool-pair split** → the offset begins after a `toolResult` whose `toolCall` precedes it → orphaned `function_call_output` → codex Responses API rejects `invalid_request_error: No tool call found for function call output with call_id ...`. Opus (messages API) tolerates it, so it only surfaces on codex. [was 0h7b]

Compaction itself pairs tool_call/result correctly (see features/session/compaction_logging.feature "Compaction keeps toolCall and toolResult together"). The orphan is introduced by the OFFSET READ, not compaction — hence same fix site.

## Desired behavior (unified)

`read-transcript-from-offset` (and the effective-head assembly) must always emit a valid, self-consistent head:
- snap the offset to a line boundary (never parse a partial line), AND
- ensure tool-call/result consistency — drop an orphaned tool-result (a function_call_output with no matching call in the head), or extend the head to include the call.

Prefer: store the boundary as an entry id / line index (not a raw byte offset) so it can neither drift mid-line nor mid-pair; plus a defensive drop of any orphaned result at render.

## Acceptance scenarios (isaac-agent) — reuse features/session/compaction_logging.feature machinery

1. Given a session whose effective-history-offset falls mid-line, when a turn builds the prompt, then it reads from the next line boundary and does NOT throw (no partial-line parse).
2. Given the effective head begins after a toolResult whose toolCall precedes the offset, when the prompt is built, then the orphaned toolResult is omitted from the active head (assert via `session has active transcript matching:` — orphan absent; no dangling function_call_output; request valid for the responses API).
3. Given a normally paired toolCall/result in the head, both are preserved (regression).

Reuses `session has transcript:` (toolCall id + toolResult call-id columns) and `session has active transcript matching:`. Net new steps: ~0.
