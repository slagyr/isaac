---
# isaac-63f3
title: Session history offset drifts mid-line and bricks the session (JsonParseException)
status: completed
type: bug
priority: high
created_at: 2026-07-05T16:16:48Z
updated_at: 2026-07-05T16:48:06Z
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

Implemented in isaac-agent `main` commit **1625017** (base 96c164d), both in
`src/isaac/session/store/impl_common.clj`:

**Read-side snap (the highest-value fix, per the bean).** `read-transcript-from-offset`
now snaps the offset **back to the start of the line it lands in** (byte after the
preceding newline, or 0) before splitting/parsing — new `snap-to-line-start`
helper. This is exactly what the manual fix did (snap 9273989 → 9273657, the line
start) and it preserves all history. Robust to a drifted offset from ANY source,
so it unbricks the session and can never parse a partial line.

**Write-side correctness (root cause, bean option #3).** `splice-compaction!` now
computes the stored `:effective-history-offset` over the **actual written prefix**
(`take-while` up to the compaction entry in `new-transcript`), not a
re-serialization of the raw `before`. `drop-orphan-toolcalls` + parentId
reparenting mutate what lands on disk, so measuring the real prefix keeps the
offset byte-exact on an entry boundary.

**Tests:**
- `spec/isaac/session/store/impl_common_spec.clj` (new) — read-side: reads from
  offset 0 / an exact boundary / **a mid-line offset (real regression: red before
  the fix — JsonParseException — green after)** / past-EOF.
- `index_impl_spec.clj` — write-side invariant: after a `:retain` compaction the
  stored offset lands exactly on a line boundary and `read-transcript-from-offset`
  round-trips cleanly (compaction entry first).

**On the acceptance's "gherkin":** the fault is in the transcript byte-reading
layer, so the two scenarios are tested precisely as unit specs (scenario 1 =
mid-line regression; scenario 2 = boundary+round-trip invariant). The happy-path
offset round-trip is *already* exercised by existing features
(`history_retention` / `compaction_strategies` "active transcript matching",
which read via the compaction-stored offset). A gherkin scenario that injects a
raw mid-line byte offset would need a new contrived step poking a byte value into
a session — implementation detail in gherkin, which the project avoids. Happy to
add one if you want it.

**Verification:** isaac-agent `bb verify` — config-bypass-lint ok; **1150 spec
examples / 2254 assertions, 0 failures**; **576 feature examples / 1292
assertions, 0 failures**; `bb lint` src clean.
