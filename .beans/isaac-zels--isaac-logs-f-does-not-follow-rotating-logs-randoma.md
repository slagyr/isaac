---
# isaac-zels
title: isaac logs -f does not follow rotating logs (RandomAccessFile held open; freezes at rotation)
status: in-progress
type: bug
priority: normal
tags:
    - unverified
    - logging
    - foundation
    - work-2
created_at: 2026-07-01T16:53:01Z
updated_at: 2026-07-01T17:00:18Z
---

## Symptom

`isaac logs -f` (follow) **freezes at log rotation.** Observed on zanebot: the follow stalled
with its last line at `19:19:20  :server/response-sent` — exactly when `server.log` rotated to
`server-20260630.log` and a fresh `server.log` was created. The server kept writing to the new
`server.log`, but the follow produced nothing further (looked like the server had frozen; it
had not).

## Cause (log_viewer.clj)

`tail-open-file!` opens the target **once** via `(java.io.RandomAccessFile. file "r")` and, when
`follow?`, loops reading new bytes from that same handle (`Thread/sleep` + `recur`, lines
~210–227). It follows by **file handle / inode** (`tail -f`), not by **name** (`tail -F`). On
rotation the file is renamed (→ `server-YYYYMMDD.log`) and a new file is created at the same
path; the held `RandomAccessFile` keeps reading the **old inode** (now the archived file, no
longer growing) → the follow appears frozen. There is no inode re-stat, no shrink/truncation
detection, and no re-open of the path.

## Fix direction

- **Follow by name:** in the follow loop, periodically re-stat the path; if the file identity
  (inode/dev) changed, or the file shrank / was replaced, **re-open the path** and continue from
  the top of the new file (like `tail -F`).
- Handle the brief "path momentarily missing" window during rotation gracefully (retry-open).
- The rotating server log (kxre/gfsq) introduced rotation; the viewer's follow was never
  updated to survive it.

## Related

- isaac-3692 — the `isaac logs` command redesign; rotation-aware follow should be part of the
  reworked viewer.
- The server-log rotation feature (kxre / gfsq) that this must follow across.


## Implementation (work-2)

- `log_viewer.clj`: follow loop re-stat path via `file-key`; on inode change,
  shrink, or brief missing path during rotation, close and re-open RAF at path.
- Spec: rotation test moves active log to archive and creates fresh file at path.
- Pushed: isaac-foundation `4ee7e92`
