---
# isaac-pl8x
title: 'isaac-google inbox worker: a pending record no handler claims is re-read every 2 s and warns :google/handler-missing forever — park it once; the smoke probe gets a no-op handler'
status: completed
type: bug
priority: high
tags:
    - google
    - ops
created_at: 2026-09-23T13:23:51Z
updated_at: 2026-09-23T16:07:03Z
---

## Observed (yopp, 2026-09-23 03:00–03:22Z)

`isaac google smoke --send-live` published probe 20295779321476340 (type isaac.google.smoke/probe). The door accepted it into inbox/pending and the smoke reported PASS. From then on the inbox worker (2 s cadence) logged `:google/handler-missing` for that record on every tick — about 1,800 warnings an hour — because no handler claims that type and an unhandled record stays pending. Stopped by hand: the record was moved to google/inbox/unhandled/.

## Change

- Worker: a record whose type has no handler is moved to `inbox/unhandled/` on first sight with ONE warning (`:google/handler-missing` with type and id), never re-read. `isaac google status` and the smoke's inbox check report the unhandled count.
- The smoke probe type gets a no-op handler in isaac-google (like the heartbeat: record arrival, mark done), so `--send-live` leaves nothing behind.
- Heartbeat records must never reach the inbox at all (they do not today; keep the scenario).

## Scenarios

- a pending record of an unknown type → one warning, record in unhandled/, next tick logs nothing.
- a smoke probe → handled, done/, no warning.

## Acceptance

bb spec / bb features / bb ci green in isaac-google; one-time on yopp: run the smoke with --send-live and confirm the log stays quiet afterwards.

## Handoff (worker, 2026-09-23)

Branch: `bean/isaac-pl8x` in isaac-google (worktree
`isaac-google-isaac-pl8x`), pushed to `origin/bean/isaac-pl8x`, commit
48625f2 (mentions both isaac-pl8x and isaac-8zl8 — same branch, same
commit). Manifest bumped to 0.1.13.

- `src/isaac/google/inbox.clj` — `unhandled-path`, `mark-unhandled!`,
  `unhandled` (mirrors `pending`), `status` now also reports `:unhandled`.
- `src/isaac/google/worker.clj` — `tick!`'s nil-handler branch now calls
  `inbox/mark-unhandled!` after the warning, so the record leaves `pending/`
  and the next tick never sees it again.
- `src/isaac/google/smoke.clj` — `PROBE-TYPE` constant, `noop-handler`
  (record arrival is already the door's job; the worker marks it done once
  the handler returns nil). `decide-inbox` gained `:unhandled` — reported in
  evidence, never gates pass/fail (an unhandled record is parked, not
  backlog).
- `resources/isaac-manifest.edn` — contributes
  `{"isaac.google.smoke/probe" isaac.google.smoke/noop-handler}` under
  `:isaac.google/handler` (module contributing to its own berth, same
  pattern as `:isaac.google/scopes`).
- `src/isaac/google/cli.clj` — `run-smoke` passes `inbox/unhandled` into
  `decide-inbox`; `run-status` prints `inbox: N unhandled`.

Scenarios (in `spec/isaac/google/worker_spec.clj`, context "a record whose
type has no handler (isaac-pl8x)"):
- "moves the record to unhandled/ with one warning" — accepts an
  `unknown/type` record, ticks once, asserts it's gone from `pending/`,
  present under `unhandled/`, and exactly one `:google/handler-missing` log
  entry.
- "stays silent on the next tick — the record is no longer in pending/" —
  ticks twice, still exactly one warning.
- smoke probe coverage is `spec/isaac/google/smoke_spec.clj` "google smoke —
  probe handler (isaac-pl8x: --send-live leaves nothing behind)":
  `noop-handler` returns nil (the worker's existing done-path handles the
  rest — no new integration spec needed since the manifest contribution is
  asserted directly in `module_spec.clj`).
- `spec/isaac/google/inbox_spec.clj` and `module_spec.clj` also gained
  scenarios for `mark-unhandled!`/`unhandled`/`status :unhandled` and the
  manifest contribution respectively.

Heartbeats: confirmed already covered — `http_spec.clj` "records the
heartbeat and keeps nothing for the worker" already asserts
`(inbox/pending "/test/isaac")` is empty after a heartbeat push; no change
needed, nothing new to pin.

Test commands and counts (from the isaac-google worktree):
- `bb spec` → 260 examples, 0 failures, 439 assertions
- `bb features` → 36 examples, 0 failures, 160 assertions
- `bb ci` → config-bypass-lint ok, then both of the above, all green
- `bb lint` on every touched `src/` file → 0 errors (whole-project `bb
  lint` shows pre-existing "Unresolved symbol: describe/it/..." noise
  across spec files unrelated to this change — same on untouched files
  like `tools_spec.clj`; not something introduced here)

Not done (out of scope / needs a live host, per bean acceptance): the
one-time yopp check — run `isaac google smoke --send-live` and confirm the
log stays quiet afterward. No real Google calls were made from the
worktree.

Left `in-progress`, no tags, per the dispatching instructions.

## Landed on main

main-sha: isaac-google 48625f2 (0.1.13)

Planner check 2026-09-23: bb spec 260/0, bb features 36/0. Fast-forwarded; registry repinned.
