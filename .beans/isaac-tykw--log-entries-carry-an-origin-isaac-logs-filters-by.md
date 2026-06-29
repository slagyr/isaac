---
# isaac-tykw
title: Log entries carry an origin; isaac logs filters by it
status: draft
type: feature
created_at: 2026-06-29T16:06:17Z
updated_at: 2026-06-29T16:06:17Z
---

Every process using the logger (server, each CLI command in its own process, spec runs) writes to the SAME default /tmp/isaac.log. Watching the server log shows entries from unrelated CLI-command processes interleaved — and is why the prod log looked full of test/CLI noise.

## Scope
- Stamp each log entry with its ORIGIN in build-entry: process kind (:server | :cli | the command name) + :pid. So entries are separable.
- isaac logs (isaac.log-viewer) filters by origin — default to the server's own entries when watching the server (e.g. --origin server / --pid N / hide CLI-origin by default), with a flag to show all.

## Design choice (decide)
(a) ONE shared file, entries tagged + viewer filters (flexible, keeps a unified log).
(b) Separate destinations: the long-running server writes /tmp/isaac.log; short-lived CLI commands log elsewhere (own file / stderr only) so the server log is clean at the source.
Lean: tag origin (a) for filtering + default CLI commands to NOT write into the server's shared file (root-cause of the pollution). Confirm.

## Why now
Directly fixes "when I watch the server logs I don't want CLI-command entries." Also unblocks clean rotation (the other bean) by giving the server sole ownership of its file.
