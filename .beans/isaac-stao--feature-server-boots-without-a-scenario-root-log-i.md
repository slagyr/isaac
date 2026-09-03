---
# isaac-stao
title: Feature server boots without a scenario root log into the live ~/.isaac (isaac-server command.feature)
status: in-progress
type: bug
priority: high
tags:
    - isaac-server
    - test-isolation
    - unverified
created_at: 2026-09-03T23:36:58Z
updated_at: 2026-09-03T23:55:18Z
---

Likely repo: **isaac-server** (`spec/isaac/server/server_steps.clj`, `features/server/command.feature`).

## Problem

Three `features/server/command.feature` scenarios (`server command logs hello before startup`, `server command logs startup with host and port`, `Default port is 6674 when no port is configured`) run `isaac server` through `run-cli-with-stubbed-config!` with **no scenario root**. `argv-with-feature-root` only injects `--root` when the feature bean has one, so `isaac.main` resolves the default root: the real `~/.isaac` of whoever runs the suite.

Observed on zanebot 2026-09-03 (verify crew, `bb features` in `verify/isaac-server`): three server boots logged `:server/hello :root "/Users/zane/.isaac"` and wrote ~130 lines (config snapshot, boot phases, every `:berth/registration`) into the **live** `~/.isaac/logs/server.log`. On the old foundation pin that also meant every line slurped the 6MB live log (~0.8s/line, the isaac-sbn7 bug), and `bb features` timed out (exit 124 at 240s). The pin is fixed separately (isaac-server bumps foundation to 8dc5d5e); this bean is the isolation leak, which is wrong regardless of speed — test boots pollute the production log and `config/set-snapshot "server boot"` entries in it.

Locally the same three boots print `root /tmp/isaac` (whatever the developer's default root resolves to).

## Settled design

- A feature-run server boot must never resolve to the real home root. The CLI-path boot step (`run-cli-with-stubbed-config!`) takes the scenario root when one is set and otherwise uses the same per-scenario default the in-process boot step uses (`default-server-home` under `target/test-state`), always passing `--root`.
- `command.feature` keeps its scenarios as written; the fix is in the step, so any future feature that boots via the CLI path is covered too.

## Acceptance

Home: `isaac-server` (`bb features`, `bb spec`).

1. `bb features` in isaac-server never logs a `:server/hello` whose `:root` is outside the repo's `target/` (run it, then `grep ':server/hello' ~/.isaac/logs/server.log` shows no new entries from the run; every hello in the feature stdout shows a `target/` or scenario root).
2. `command.feature` scenarios still pass and their `the log has entries matching` assertions still see `:server/hello` / `:server/started`.
3. `argv-with-feature-root` (or its replacement) always yields an argv containing `--root` for the server command.

## Out of scope

- Rotating or cleaning the live zanebot `server.log`.
- The foundation pin bump (done alongside this bean, commit on isaac-server main).


## Amendment (2026-09-03, plan — combined fix with isaac-zqyw)

Blocked by **isaac-zqyw** (isaac-foundation: `apply-server!` under `:memory`
binds no file sink). Two additions to this bean, design otherwise unchanged:

- **Pin bump rides here.** Bump `io.github.slagyr/isaac-foundation` (and the
  `-spec` / `-test-support` coords) in `deps.edn` and `bb.edn` to the SHA
  isaac-zqyw records, in the same commit as the step fix.
- **One-time acceptance (not a scenario):** after a full `bb features`, the
  memory-mode scenarios (`command.feature`, `hot_reload_logging.feature`,
  `logging.feature`) wrote no `logs/server.log` anywhere — not under `target/`
  and not under the home root. `find target -name server.log` after the run
  lists only files produced by file-mode scenarios (`log_lifecycle.feature`).


## Implementation (2026-09-03, plan@isaac-plan)

isaac-server main @ **98aa691**.
- `spec/isaac/server/server_steps.clj`: new `feature-root` — the scenario
  root, else `default-server-home` under `target/test-state` (cleaned when not
  mem-fs, recorded in `g :root` like the in-process step);
  `argv-with-feature-root` always injects `--root` unless argv has one.
  `command.feature` unchanged.
- `features/server/log_lifecycle.feature`: the six file-asserting scenarios
  add `logging.output | file` to their config table (production knob) —
  required now that foundation `:memory` binds no sink (isaac-zqyw refinement).
- Pin: foundation `8dc5d5e` → `e0dc789` in deps.edn (3) and bb.edn (5).

Gate: `bb verify` — config-bypass-lint ok, spec 214/0, features 67/0.
One-time isolation check: `:server/hello` count in the real
`~/.isaac/logs/server.log` unchanged across a full `bb features` (9 → 9), and
`find target -name 'server*.log'` empty afterwards (lifecycle scenarios run on
mem-fs).
