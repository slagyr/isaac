---
# isaac-kf7q
title: "Per-session sidecar storage instead of single index file"
status: completed
type: task
priority: low
created_at: 2026-04-30T00:27:18Z
updated_at: 2026-05-09T16:03:11Z
---

## Description

Today every session has its transcript JSONL plus a single shared
index file (<state-dir>/sessions/index.edn) that holds all sessions'
metadata. Every turn writes BOTH the transcript AND the index. Two
problems compound:

1. Write contention: multiple processes (CLI, ACP server, cron)
   race on the index file. We've already lost state to embedded-Dolt-
   meets-index issues.
2. Coupled corruption: a bad write to the index can invalidate
   the catalog of every session.

## Proposal

Split the index into per-session sidecar files:

  <state-dir>/sessions/<id>.jsonl    transcript (unchanged)
  <state-dir>/sessions/<id>.edn      lightweight metadata sidecar
                                       (crew, model, total-tokens,
                                       compaction-count, last-channel,
                                       updated-at, cwd, origin, etc.)

list-sessions globs the .edn sidecars. Sort-by-updated-at reads each.
Concurrent writers don't collide — each session updates its own
sidecar independently. Delete is 'rm <id>.{edn,jsonl}'.

## Why deferred

The index file is a working performance optimization at Isaac's
current scale (tens of sessions, occasional reads). Refactoring
touches every read/write seam in src/isaac/session/storage.clj
plus migration of existing on-disk data. Pain isn't yet worth the
gain.

## Surfaces (when picked up)

- src/isaac/session/storage.clj: rewrite list-sessions, get-session,
  update-session!, append-message!, append-error!, append-compaction!,
  create-session!
- Migration: existing index.edn -> per-session .edn sidecars
- spec/isaac/session/storage_spec.clj: lots of test churn

## Triggers to revisit

- Multi-machine state loss recurring (we lost state once during
  the embedded-mode swap)
- Performance with N sessions in the high hundreds
- Concurrent-write bugs surfacing under multiple workers

## Status

Deferred. Track here so the design conversation isn't lost.

## Notes

Session sidecar implementation is complete and the rewritten storage.feature scenario passes. Full bb features still has unrelated runtime-state regressions now tracked in isaac-t4oj; this bead should not stay open for that separate issue.

## Scenarios (in isaac-agent/features/session/storage.feature)
1. "Session sidecars are keyed by session id" (features/session/storage.feature:205)
   reuses: "the session store uses the file implementation", "the following sessions exist", "the file {path:string} exists"; no new steps.
   Review: rewrite (2026-06-29). Use general root-relative file existence checks ("the file \"sessions/<id>.edn\" exists") instead of dedicated "the session sidecars exist for" collector. The special step is now unused (only ever referenced here). Still directly proves per-id .edn sidecars are created. Supports decoupling goals.
   (Feature scenario text updated on review approval.)

