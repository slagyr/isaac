---
# isaac-k0xm
title: 'isaac-hail: unreadable hail records are refused at send and quarantined by the router — never retried every tick'
status: completed
type: bug
priority: high
created_at: 2026-09-25T02:31:52Z
updated_at: 2026-09-25T02:37:33Z
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

## Implementation (scrapper@isaac-work-1, 2026-09-25)

isaac-hail branch `bean/isaac-k0xm` @ 8c70cca (on top of origin/main 48faa9f). Worktree: `~/agents/isaac/work-1/isaac-hail-k0xm`.

- `cli.clj`: `flag-name`/`flag-keyword` strip exactly one leading colon for `--session-tag`, `--session`, `--crew` (stays a string, as before), `--reach`. `keyword-flag-errors` refuses any value whose keyword does not read back (`:::x`, `::yopp`) with `Invalid --<flag> value …`.
- `queue.clj`: `serialize-readable` round-trips `write-edn` through `edn/read-string` (and requires equality); throws `ex-info` `{:type :hail/unreadable-record :reader-message …}` before any pending write / `:hail/sent`. Public `check-readable!` backs `--dry-run`.
- `tool/hail.clj` returns `{:isError true :error <msg>}`; `http.clj` returns 400 with the message.
- `router.clj`: `list-pending` only reads `*.edn`; an unreadable file is moved to `hail/undeliverable/<id>.edn`, logged once `:hail/bad-record :id :path :error :quarantined true`, and `attention/maybe-notify-dead-letter!` gets `{:id <from filename>}` plus `:data/:params/:thread-id` salvaged from a best-effort re-read with `::` collapsed to `:`.
- Manifest 0.1.22 + CHANGELOG.
- Specs: cli (5), queue (1), router (1), tool (1), http (1). Features: send-addressing.feature (outline + 2), router.feature (1). The new scenarios fail on pre-fix src (4 failures) and pass after.

Results: `bb ci` → spec 182/0, features 141/0 (2 pending, pre-existing). `bb lint`: 79 errors vs 76 on origin/main. All are pre-existing speclj `:refer :all` "Unresolved symbol" (no kondo speclj config); the +3 are the same class in new spec forms. No new src findings. Lint is not green on main either; that needs its own bean (kondo speclj config).

## Verified (perceptor@isaac-verify, 2026-09-24)

- Tested isaac-hail bean/isaac-k0xm @ 8c70cca (base origin/main 48faa9f). `bb ci` EXIT 0: spec 182/0, features 141/0 (2 pending, pre-existing).
- Acceptance scenarios present, not @wip: send-addressing.feature (colon outline, `:::x` refusal naming `--session-tag`, `--crew`/`--session`), router.feature (quarantine once across 3 ticks, other record still routes). Specs cover queue refusal (no pending file, no `:hail/sent`) and router quarantine.
- `bb lint`: 79 errors on branch vs 76 on origin/main (reproduced). All errors are speclj `:refer :all` "Unresolved symbol"; the +3 (`should-contain`, `should-not`, `should-be-nil`) are the same class. No new src findings. Accepted as pre-existing; a kondo speclj config needs its own bean.

## Landed on main (2026-09-24)

main-sha: isaac-hail 82425ece988afafc1f4b1156e41ad26122c00789
