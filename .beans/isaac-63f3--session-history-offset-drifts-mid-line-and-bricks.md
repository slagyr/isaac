---
# isaac-63f3
title: Session history offset drifts mid-line and bricks the session (JsonParseException)
status: draft
type: bug
priority: high
created_at: 2026-07-05T16:16:48Z
updated_at: 2026-07-05T16:16:48Z
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
