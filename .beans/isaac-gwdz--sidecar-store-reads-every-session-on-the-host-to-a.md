---
# isaac-gwdz
title: 'Sidecar store reads every session on the host to answer for one: get-session/get-transcript/update scan+conform all 456 session.edn (the 1.5 s compaction check)'
status: in-progress
type: bug
priority: high
tags:
    - session
    - performance
    - unverified
created_at: 2026-09-18T01:49:41Z
updated_at: 2026-09-18T01:49:41Z
---

Micah (2026-09-17, watching zanebot logs): "these compaction checks all take about 1.5 seconds … it seems like wasted effort. What is going on there?" Follow-up to isaac-h8o9, which instrumented the check and named the two fixed-cost steps (`entry-ms` = `policy/get-session` ≈ 650ms, `transcript-ms` = `policy/get-transcript` ≈ 750ms) but did not say why.

## Why (found by reading the sidecar store)

The production store is `SidecarSessionStore`. Its `get-session` was `c/get-session read-session-store …` → `read-sidecar-store`, which **scans every session directory under the root and, for every one of them, takes the persist lock, slurps, parses and schema-conforms `session.edn`** — then picks one id out of the map. `get-transcript` calls `get-session` again; `update-session!` (every token/updated-at write) did the same full read. zanebot has **456 session.edn files** (1.8MB); yopp far fewer — that is the host-dependent constant h8o9 saw (715ms vs 241ms), and why it never scaled with transcript size.

Second, smaller defect: `locate-session` ran `scan-session-dirs` unconditionally (strict `let`), even on a verified index hit — ~80ms per call on zanebot, called from persist, turn markers, sidecar writes. And a stale index row (session moved under another crew) was never repaired, so such a session scanned forever.

## Fix (isaac-agent branch `bean/isaac-gwdz`, 2 commits)

1. `locate-session`: index row is a hint verified against disk (`exists? <dir>/session.edn`); scan only on a miss or a stale row; a scan hit that disagrees with the row rewrites it. **Not a cache** — nothing new is stored; the index already existed and is validated on every call.
2. New `impl-common/read-session-entry-for`: resolve the id's location, read ONLY that session.edn. Sidecar `get-session` and `update-sidecar-entry!` use it. Unknown id → nil (+ `assert-migrated!` as before); an existing-but-blank/unparseable session.edn → throws `:session/unreadable` (isaac-4zr3 exhibit 6 — the old whole-store read silently skipped it). `list-sessions` / `most-recent-session` still read the whole store (they need it).

## Evidence — zanebot, production store, cold bb, warm calls

| session | `get-session` main → fix | `get-transcript` main → fix |
|---|---|---|
| isaac-verify (350 entries) | 1212.8 → **3.8 ms** | 1420.9 → **63.5 ms** |
| isaac-work-2 (467 entries) | 1401.1 → **2.7 ms** | 1423.9 → **90.3 ms** |

`locate-session` alone: 73–85 ms → ~0 on an index hit. What remains in `get-transcript` is the 1.3MB transcript read itself (`:session/transcript-read :elapsed-ms 84`).

## Specs (committed on the branch)
- `spec/isaac/session/store/impl_common_spec.clj` — does not scan session directories when the indexed session is on disk; repairs a stale index row when the session lives under another crew.
- `spec/isaac/session/store/sidecar_spec.clj` — reads one session without reading every session on the host (scan spy across get-session / get-transcript / update-session!); get-session is nil for an unknown id and still throws for an unreadable existing one.

## Acceptance
```
cd isaac-agent && bb spec spec/isaac/session/store && bb spec && bb features
```
Local: 1630 specs / 0 failures; 799 features / 0 failures / 1 pending. After the train: `:session/compaction-check` on zanebot shows `entry-ms` single digits and `transcript-ms` ≈ the transcript read; h8o9's 715ms constant gone.


## Handoff

branch: `bean/isaac-gwdz` @ 0110454 (base origin/main@372b03b). Implemented by the planner at Micah's request (2026-09-17); verifier: squash-merge on green, then the train (agent version bump + pin).
