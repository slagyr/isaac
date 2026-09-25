---
# isaac-k0xm
title: 'isaac-hail: unreadable hail records are refused at send and quarantined by the router — never retried every tick'
status: in-progress
type: bug
priority: high
created_at: 2026-09-25T02:31:52Z
updated_at: 2026-09-25T02:32:12Z
---

## Symptom

zanebot 2026-09-25 02:11Z–02:31Z: 1,198 `hail/bad-record` errors, one per
router tick, all for `hail/pending/f5a6c37c.edn` — "Invalid token:
::project/isaac-mcp". The record was a verify hail sent by worker
isaac-work-1 through `isaac hail send --session-tag :project/isaac-mcp`.
Quarantined by hand (moved to `hail/undeliverable/`) to stop the flood.

## Cause (three layers, all in isaac-hail)

1. `isaac.hail.cli/keyword-set*` does `(map keyword values)`. A tag typed
   with its leading colon — `:project/isaac-mcp`, the natural thing to type
   — becomes `(keyword ":project/isaac-mcp")`, whose name starts with a
   colon and prints as `::project/isaac-mcp`. Same for `--crew`,
   `--session` and the seq variant on line 78.
2. `isaac.hail.queue/send!` writes `(write-edn record)` to pending and
   persists it without reading it back. `--dry-run` "validates" without
   ever testing that the record survives `edn/read-string`.
3. `isaac.hail.router/read-record` logs `:hail/bad-record` and returns nil;
   `list-pending` re-scans every tick, so an unreadable file is re-logged
   forever and never leaves pending. A record no reader can parse is
   poison, not weather — it belongs in `hail/undeliverable` with dead-letter
   attention ([[hails-never-die]] is for infrastructure failures).

## Design

- CLI: keyword coercion strips leading colons (`:foo` and `foo` both →
  `:foo`); a value that still doesn't read back as a keyword is a usage
  error naming the flag. Applies to every keyword/keyword-set flag.
- Writer: `send!` (and the `--dry-run` path) round-trips the serialized
  record through `edn/read-string` before persisting; failure throws
  ex-info `:hail/unreadable-record` with the reader message — nothing is
  written to pending. The hail-send tool surfaces the same message.
- Router: on an unreadable pending file, move it to `hail/undeliverable`
  once, log `:hail/bad-record` once with `:quarantined true`, and post
  dead-letter attention when the delivery's data names a notification-comm
  (best effort — the record can't be parsed, so the id from the filename is
  what attention gets).

## Acceptance (isaac-hail spec + feature)

- [ ] `isaac hail send --band x --session-tag :project/foo --dry-run` prints
  `:session-tags #{:project/foo}`; `--session-tag project/foo` prints the
  same; a tag like `:::x` is refused with a message naming `--session-tag`.
- [ ] `--crew :yopp` and `--session :abc` coerce the same way.
- [ ] `queue/send!` with a record whose serialized form does not read back
  throws `:hail/unreadable-record`; pending has no file, no `hail/sent` log.
- [ ] Router tick over a pending file containing `#{::a/b}`: the file is in
  `hail/undeliverable` after one tick, `:hail/bad-record` logged exactly
  once across three ticks, other pending records still route.
- [ ] Version bump, `bb spec`, `bb features`, `bb lint` green.

Likely repo scope: isaac-hail (cli.clj, queue.clj, router.clj, spec,
features). No agent/foundation changes.
