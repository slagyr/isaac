---
# isaac-1hs0
title: 'Log level is not tunable: add config key, CLI flag, and viewer filter'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-09-16T15:15:03Z
updated_at: 2026-09-16T16:52:28Z
---

## Problem

Nothing in isaac can set the log level. `isaac.logger` keeps one global level in a state atom defaulting to `:debug`; `enabled?` is a single rank comparison over `{:report 0 :error 1 :warn 2 :info 3 :debug 4}`. `set-level!` is called in exactly ONE place in foundation — the `capture-logs` test macro — and greps across isaac-agent and isaac-claude-code find no callers at all. The only config read of `[:log]` is `logs/cli.clj`, which uses `:file`/`:output` solely to choose which file `isaac logs` tails. The CLI's global options are `--root`, `--log-file`, `--help`; there is no level flag. zanebot launches `isaac.main --root /Users/zane/.isaac server` with no logging flags and has no `:logging` config at all.

Result: every isaac process everywhere logs at debug, permanently. Measured on zanebot 2026-09-16: 1342 debug lines vs 144 info, 14 warn, 11 error in five minutes — 89% debug. `server.log` holds 225,688 lines since Sep 10 (~37k/day). Rotation works (daily `server-YYYYMMDD.log`), so this is an attention problem, not a disk problem.

## Decisions (2026-09-16, Micah)

- **Add the level config key and the CLI flag.** Both wanted.
- **Add a level knob to `isaac logs` too** — "even if all the debugs are being written, when we read them it can filter to the level that we're looking for." Writing stays generous; reading gets selective.
- **No `:trace` level.** Considered and declined: with the default staying `:debug`, anything demoted to trace would stop being written to disk, losing exactly the per-cycle timings that isolated the 2.5s cycle gap (isaac-3uy9). The viewer filter solves the reading problem without a new level, and a fourth level makes every future log call a harder classification decision.
- **Default level stays `:debug`.** Flipping it is a fleet-wide behavior change and is deliberately NOT part of this bean.
- Micah, on log purpose: the logs are how he checks things are working and how agents answer "what happened" — so more information on disk is better; the fix is filtering at read time.

## Proposal

1. **`:logging {:level :info}`** resolved in `isaac.log.output` beside the existing `output-from-config`, and applied by BOTH `apply-cli!` and `apply-server!` — the single chokepoint the CLI and server already pass through.
2. **`--log-level LEVEL`** as a global flag: a third branch in `isaac.cli.args/extract-root-flag` (hand-rolled, so it needs the `--log-level=value` form too), threaded through `main/configure-cli-logging!` exactly as `--log-file` is.
3. **`isaac logs --level LEVEL`**: filter entries at or above LEVEL. `viewer/tail!` currently accepts only `:color? :follow? :zebra? :plain? :limit` and has no filtering concept, so this is new behavior. It must apply to `--follow` as well as the initial dump, and `--plain` (raw passthrough) should bypass it.

Precedence: `--log-level` flag > `:logging {:level}` config > default `:debug`.

## Acceptance

- `bb spec spec/isaac/log/output_spec.clj` — level resolved from `:logging {:level}`, defaulting to `:debug`, unknown values falling back to the default (mirrors the existing `output-from-config` specs).
- `bb spec spec/isaac/logger_spec.clj` — `enabled?` honours a level set through the new path.
- `bb features features/logs/cli.feature` — `isaac logs --level warn` on the existing multi-level fixture prints the error and warn entries and not the info/debug ones; `--level debug` prints all; `--plain` ignores the filter.
- `bb spec` covering `cli/args.clj` — `--log-level info` and `--log-level=info` both strip from args and surface the value; unknown level values are rejected or ignored explicitly (decide in review, do not leave implicit).
- `bb ci` green in isaac-foundation.
- Manual: `isaac --log-level info <cmd>` writes no debug entries; `isaac logs --level warn` filters an existing debug-heavy file.

## Open — not decided

- **`ISAAC_LOG_LEVEL` env var.** `ISAAC_LOG_FILE` already exists and `apply-cli!` honours it, so an env twin would be consistent, but nobody asked for it. Decide before implementing; if added, precedence sits between flag and config.
- **Unknown/foreign levels in the viewer.** `features/logs/cli.feature` already feeds the viewer a `:level :trace` entry even though the logger cannot emit one. The filter needs a defined answer: treat unknown levels as more verbose than `:debug` (so `--level info` hides them) is the suggested rule.

## Note

Deploying foundation is heavier than a module: version bump, tag, manual `gh workflow run Release`, homebrew-tap dispatch, then `brew upgrade` + relink on zanebot. See [[isaac-deploy-train]]. Batch this with other foundation work rather than shipping alone.

## Scenarios (committed @wip, 2026-09-16)

isaac-foundation `features/logs/cli.feature` — 4 scenarios, all reusing existing steps (`isaac is run with …`, `a file … exists with content:`, stdout assertions). No new steps.

1. **--level shows that severity and above** — `--level warn` on a four-level fixture prints the error and warn entries, not info or debug.
2. **--level debug shows everything.**
3. **an unknown level is more verbose than debug and is hidden above it** — settles the parked question with the suggested rule: the file already contains a `:level :trace` entry the logger cannot emit; `--level info` hides it.
4. **--plain bypasses level filtering** — raw passthrough stays raw.

The config key and the `--log-level` flag stay at spec level (`log/output_spec.clj`, `logger_spec.clj`, `cli/args.clj` specs) as the acceptance section already states — the harness logs to memory, so asserting "what got written" through a feature would test the harness rather than the behavior.

## Worker evidence (2026-09-16)

Implemented config and global CLI log-level precedence, server propagation, `isaac logs --level` filtering for initial and follow output, post-filter limits, unknown-level handling, plain bypass, manifest/help docs, and the direct configured-path logger assertion. Removed only the four authorized `@wip` tags.

- branch: `bean/isaac-1hs0` @ `2985d7e70d8a386758573411498bb755511b3985` (base `origin/main@0c25934e6175c08b497f5ac53b3707aabe9ad515`)
- focused affected specs: 143 examples, 0 failures, 256 assertions
- `bb features features/logs/cli.feature`: 21 examples, 0 failures, 52 assertions
- `ISAAC_TEST_TIMEOUT_MS=180000 bb ci`: 1031 specs / 188 features, 0 failures (2 pre-existing pending scenarios)
- CI prerequisite repair: first feature run found a stale global tools.gitlibs mirror pointing at a deleted worktree; removing that cached fixture mirror made the clean rerun green. No repository change was required.
- manual global write filter: debug invocation wrote one debug entry; `--log-level info` wrote zero debug entries
- manual viewer filter: `isaac logs --level warn` showed warn/error and hid debug
- `git diff --check`: clean

Ready for verification.
