---
# isaac-4zr3
title: 'Conversation persist lock + crash-safe writes: session.edn/segment spit still racy after jz6h'
status: draft
type: bug
priority: high
tags:
    - durability
    - session
    - hail
created_at: 2026-09-05T16:46:04Z
updated_at: 2026-09-05T16:46:04Z
blocked_by:
    - isaac-jz6h
---

Follow-up to isaac-jz6h (append lock landed; hail-failover + quarantine still open). Parallel tool threads still share the session files without a conversation-wide lock, and whole-file writes are truncate-then-write.

## Decision (2026-09-05, Micah)

Both requirements, not just the lock:

1. **Conversation lock.** One mutex per conversation (session id). Every persist *and* every read of that conversation's files takes it. Parallel tools still *run* in parallel; they queue only at the store. Then `append-entry!` / sidecar spit don't have to be concurrency-aware. Lock reads too — otherwise the skeleton `session.edn` race stays.
2. **Crash-safe writes.** Orthogonal to the mutex: temp+rename for whole-file rewrites; one-syscall full-line append. A lock does not buy crash-mid-write (restart tore lines).

In-process JVM lock first. A second process (`isaac sessions set` while the server is turning) won't see a JVM lock; do not invent flock unless we later care.

## What's still unlocked after jz6h

jz6h locked *transcript appends* (`append-entry!` builds the full EDNL line then `locking` a per-path monitor around `spit :append`). Live agent still:

- `with-transcript-lock` is a no-op unless a compaction is in flight (`drive/turn.clj`).
- `session.edn` is `sidecar.clj` `write-sidecar!` → `c/spit*!` → `fs/spit` (truncate-then-write).
- Segment / `write-ednl!` is a whole-file spit (`impl_common.clj`).

## Exhibit 6 (2026-09-05 04:00:56Z, astute-birch)

Work turn on isaac-1sdl re-queue (hail 2f9c8e52) failed at turn end with `Unconformable entity {:updated-at "2026-09-05T04:00:56", :tags #{}, :key "astute-birch", :name #ValidateError{"must be present"}, :created-at nil, …}`. On disk, session.edn was healthy before and after. A reader got nil/blank mid-rewrite, synthesized a skeleton, failed validation. Requirement: session.edn writes are atomic (temp + rename) and a blank/unparseable read of an EXISTING session must throw `:session/unreadable`, never mint a skeleton.

## Out of scope (already beans)

- Hail rebind-on-poison / attempt burn — leftover jz6h hail-failover leg; do not reopen jz6h.
- Torn-line quarantine at open — leftover jz6h quarantine leg.
- Bound-unclaimed deliveries — isaac-at5m.
- Provider "closed" burns attempts — isaac-9gcs.

Do not implement from this draft. Promote to todo after scenarios exist (`/plan-with-features`).
