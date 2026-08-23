---
# isaac-sbn7
title: 'Server log rotate off the write path: boot+timer, fs/size, never CLI'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-08-23T01:08:37Z
updated_at: 2026-08-23T01:15:28Z
---

Likely repo: **isaac-foundation** (`isaac.log.file`).

## Problem

`write-entry!` calls `prepare-active-log!` on **every** server log line. That rotate check slurps `server.log` twice and `edn/read-string`s every line (byte-size via `count` of slurp; file-day via parse-all). Boot looks like ~600ms per berth registration once the file is ~8MB. Same tax on every turn.

tqm1 (2026-06-29) asked for bounded logs. Its Gherkin rotated **on server start**. The worker hung rotate off the write path.

## Settled design (2026-08-22, Micah)

- **Append is append.** `write-entry!` does not rotate, slurp, or parse.
- **Size/day rotate uses `fs/size` + clock**, never file contents (first-line date at most, if day-roll needs it).
- **When:** (1) `configure-server-sink!` / server boot; (2) a ~1 min timer in that **same long-lived server process**.
- **Not on CLI.** `isaac prompt`, `sessions`, `modules`, … must not rotate `server.log` and must not start a timer. tqm1 sole ownership: the server owns the file. CLI file sink (`logs/cli.log`) stays unrotated.
- Defaults unchanged: `max-bytes` 100MB, daily roll in config `:tz`, `max-days` 30 retention.

## Acceptance

Home: `isaac-foundation` `spec/isaac/log_file_spec.clj` (`bb spec spec/isaac/log_file_spec.clj`).

1. **write-entry!** appends one line to an already-oversized same-day `server.log` and does **not** archive it.
2. **write-entry!** does not `fs/slurp` the active log.
3. **configure-server-sink! / prepare-active-log!** (server boot) archives when the file's day ≠ today, and when `fs/size` > max-bytes.
4. **rotation-tick!** (timer handler) performs the same size/day/retention check; does not slurp the whole file.
5. **CLI sink** (`configure-cli-sink!`) does not start the rotation timer and does not call server rotate.
6. Retention still drops archives older than max-days on boot/tick, not on append.

## Out of scope

- Changing max-bytes / max-days defaults
- Rotating `cli.log`
- zanebot `:tz` (already `America/Phoenix`)


## Implementation (plan, 2026-08-22)

isaac-foundation `src/isaac/log/file.clj`:
- `write-entry!` appends only
- `prepare-active-log!` / `rotation-tick!` use `fs/size` + first-line date
- `configure-server-sink!` rotates once and starts a 60s daemon timer
- `configure-cli-sink!` / `clear-sink-config!` do not start a timer; CLI stops any leftover timer
- `*rotation-tick-ms*` nil in tests

`bb spec spec/isaac/log_file_spec.clj` — 12 examples, 0 failures.
