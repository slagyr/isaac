---
# isaac-gar0
title: Local isaac routes into the running server (cold only when down; stale basis refuses mutators)
status: draft
type: feature
priority: high
tags:
    - cli
created_at: 2026-09-17T15:55:25Z
updated_at: 2026-09-17T15:55:25Z
parent: isaac-eqkb
blocked_by:
    - isaac-qvhy
---

Child 4 of isaac-eqkb. Blocked by child 3. **This is the bean that delivers single-writer** — the remote pipe is a minor second-writer source; the major one is local: every crew tool shell-out (`isaac …`, hundreds per bean) and every SSH'd command on zanebot is a cold process writing beside the server.

## Design

- The local `isaac` entrypoint decides BEFORE booting bb whether a server for this root is up and current, and if so runs the command through it (same embedded path as child 3).
- **Server down ⇒ cold bb.** Safe: no other writer.
- **Server up, basis current ⇒ warm.** Basis = foundation version + module SHAs (reuse tki3's `:basis`); config mtimes EXEMPT (server hot-reloads; warm reads are MORE current than cold).
- **Server up, basis stale ⇒** read-only commands run cold; mutators refuse: "server restart pending — restart, or stop the server to run cold". Needs a per-command `:mutates true|false` (or per-subcommand) manifest hint; default = mutates.
- `:local-only` commands always run cold locally (that is what local-only means).
- Local auth: the server writes a 0600 runtime file under the root (port/socket + token + basis) at boot, removed on stop; stale file (pid dead / connect refused) ⇒ treat as down.

## Open question (resolve before promotion)

Transport for a client that must not boot bb. curl cannot speak WebSocket. Options: (a) add a plain-HTTP streaming twin of `/cli` for non-interactive commands (`curl -N`, chunked; stdout/stderr/exit multiplexing needs a tiny line protocol the shell shim demuxes) and keep interactive commands on bb+WS; (b) unix domain socket + small compiled client shipped in the brew formula; (c) accept bb boot (~30 ms bare bb, per isaac-v1la's table) and do the check in a minimal bb script that requires NOTHING from isaac until it has decided — likely the pragmatic answer since bare bb is 30 ms and bb has a WS client. Recommend (c); measure first.

## Acceptance (draft — scenarios at promotion)

- with a current server up, `isaac sessions list` performs zero config resolutions in the client process (v1la's resolution spy) and the server logs `:cli/command-started`.
- with no server, the same command runs cold and succeeds.
- stale basis: a read runs cold; a mutator exits nonzero with the restart-pending message and writes nothing.
- stale runtime file ⇒ cold, no hang (connect timeout bound).
- zanebot: `/usr/bin/time -p isaac --version` and `isaac sessions list` recorded before/after.

Likely repo scope: isaac-foundation (launcher + runtime file + manifest hint), isaac-cli-server, brew formula wrapper.
