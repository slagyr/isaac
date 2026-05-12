---
# isaac-a9y
title: "Filesystem abstraction: in-memory for tests, real for production"
status: completed
type: feature
priority: normal
created_at: 2026-04-14T01:05:54Z
updated_at: 2026-04-14T04:41:47Z
---

## Description

## Problem

Specs are slow (1.7s, was 0.2s). The bottleneck is `chat_spec.clj` doing 22 `create-session!` calls with real disk I/O — 0.95s of the total 1.7s. Every session creation writes an index file and a JSONL transcript to disk.

## Design

Introduce a filesystem abstraction protocol:

```clj
(defprotocol Fs
  (read-file [fs path])
  (write-file [fs path content])
  (append-file [fs path content])
  (file-exists? [fs path])
  (list-files [fs dir])
  (make-dirs [fs path])
  (delete-file [fs path]))
```

Two implementations:
- **RealFs** — delegates to `clojure.java.io`, `spit`, `slurp`. Production use.
- **MemFs** — atom wrapping a map of path→content. Tests use this.

### Integration

Session storage (`src/isaac/session/storage.clj`) takes an Fs instance instead of calling `spit`/`slurp`/`io/file` directly. The Fs is part of the config/context — tests bind MemFs, production uses RealFs.

Tool builtins (read, write, edit, exec) should also use the Fs abstraction eventually, but session storage is the immediate win.

## Acceptance

- `bb spec` runs in under 0.5s
- `bb features` still passes
- No test writes to disk for session I/O (MemFs only)
- RealFs is the default in production

## Acceptance Criteria

bb spec under 0.5s. All specs and features pass. Tests use MemFs for session I/O.

## Notes

Verification failed (third attempt): timing runs at 0.533s, 0.530s, 0.517s — still above 0.5s target. Commit af2c8cd ('Cut test suite from 0.63s to 0.50s') was applied and shows improvement from 0.63s, but cannot confirm under 0.5s reliably. Very close — one more optimization pass should get there.

