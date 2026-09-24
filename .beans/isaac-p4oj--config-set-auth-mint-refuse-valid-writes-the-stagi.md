---
# isaac-p4oj
title: 'config set / auth mint refuse valid writes: the staging validation never sees <root>/.env, so every ${VAR} reference resolves to unset'
status: in-progress
type: bug
priority: critical
tags:
    - unverified
created_at: 2026-09-24T13:37:58Z
updated_at: 2026-09-24T14:22:02Z
---

Micah, 2026-09-24 on zanebot: `isaac http auth mint nightbird-cli --scopes cli` printed
`[{:key "comms[:discord][:discord/token]", :value "is required when type is discord", …}]` and wrote nothing. The live config is valid — `:discord/token "${DISCORD_TOKEN}"` resolves from `<root>/.env`.

## Cause

`isaac.config.mutate/validate-plan` copies only `config/` into a fresh in-memory staging fs, then loads the config there. `loader` calls `env/lock-dotenv! root` against the staging fs (loader.clj:208), where `<root>/.env` does not exist, so every `${VAR}` reference is unresolved → treated as unset (isaac-rxun) → conditional-required fields error. The rxun filter in `set-config` (`unresolved-reference-paths`) drops errors keyed by the unresolved path, but this error is keyed `comms[:discord][:discord/token]` with a different message, so it survives as a "new" error and the write is refused. The http `auth mint`/`rotate`/`revoke` commands sit on `set-config`, so they fail on any host whose config uses `${VAR}` for a required field — i.e. every production host.

## Fix

- `validate-plan`: copy `<root>/.env` (and anything else `${file:…}` references may need under root — decide: copy the whole root minus sessions/logs, or `.env` explicitly) into the staging fs before loading. Prefer explicit `.env` plus the existing declared local modules.
- Regression scenario in isaac-foundation `features/cli/…`: a config with `:discord/token "${DISCORD_TOKEN}"`-style required-when field satisfied via `.env`; `isaac config set <other key> <value>` succeeds; the same set on a root without `.env` reports the reference as unresolved (warning) rather than a required-field error.
- isaac-http: `print-secret-or-error!` prints `(pr-str errors)` — render each error as `<path>: <message>` on stderr instead of a raw vector.

## Acceptance
- [ ] scenario above green; `bb ci` green in isaac-foundation
- [ ] `isaac http auth mint x --scopes cli` on a root whose config carries a `${VAR}` required field succeeds and prints the secret once
- [ ] `isaac http auth mint` failure output is one line per error, no EDN vector
- [ ] foundation tagged/pinned; zanebot's brew keg rebuilt (Micah)

Repo scope: isaac-foundation (`config/mutate.clj`, features), isaac-http (`cli.clj`).

## Preferred implementation (Micah + planner, 2026-09-24)

No `--force` path — fix the validator, then mint. Prefer NOT copying `.env` into the staging fs: the process already holds the locked dotenv snapshot (`env/lock-dotenv!` ran for the live load). Make the staging load reuse that snapshot — e.g. `loader/load-config-result` takes an `:env` (or `:dotenv`) option that `validate-plan` passes from the live root, and `lock-dotenv!` is skipped when it is supplied. Copying the file is the fallback if the loader cannot be threaded that way. Either way nothing leaves the process; the mem-fs dies with the call.


## Handoff

Both branches pushed as `bean/isaac-p4oj` (not repinned in isaac-http; planner repins isaac-foundation dep after this lands).

**isaac-foundation** — `b81e021c7624017782c3b00ebc467df7bec57bf7`
**isaac-http** — `6c4eeafa7abb97add2c6e5481bd9cf763b1e4853`

### Approach: snapshot, not copy

Implemented the preferred design — no `.env` file copy. `isaac.config.env/lock-dotenv!` now has a 2-arity: `(lock-dotenv! root snapshot)` installs `snapshot` directly (skips reading the fs); added `env/dotenv-snapshot` to read the current locked map. `loader/load-config-result` takes an optional `:dotenv` opt — when supplied (even `{}`), it's installed via the 2-arity instead of re-locking `<root>/.env` against whatever fs is active. `mutate/validate-plan` captures `(env/dotenv-snapshot)` *before* switching into the staging mem-fs (the live root's dotenv is already locked there, from the `current` load a few lines earlier in `set-config`/`unset-config`) and threads it through as `:dotenv` to the staged `load-config-result` call. Nothing leaves the process; the staging fs still dies with the call.

### Root cause detail (beyond the bean's write-up)

The bug isn't just "staging can't see .env" — even with that fixed, the pre-existing isaac-rxun unresolved-ref filter in `set-config`/`unset-config` only saves a write when the required-field error's `:key` string exactly matches the unresolved-ref warning's `:key` string. For a plain top-level field (e.g. `marigold.comm.parlor`'s `:loft`) those keys already coincide, so that fixture doesn't actually reproduce the reported refusal — it passes even on unfixed code. The real-world case (`comms[:discord][:discord/token]`) involves a **namespaced** extra-schema field inside a dynamic-schema comm slot, where the required-field error key (`comms.discord.discord/token`) and the unresolved-ref warning key (`comms.discord.token` — the warning path drops the `discord/` namespace segment) diverge, so the rxun filter can't save it and the write really is refused pre-fix. Built a new fixture, `spec/isaac/config/fixtures/modules/marigold.comm.discord/`, mirroring this shape (`:p4oj-comms` table — named to avoid a `config-schema collision` against an existing classpath-loaded `:comms`/`:isaac.server/comm` fixture when running the full suite) to reproduce it faithfully. Confirmed red-before/green-after by stashing the src changes and re-running.

### Tests

**isaac-foundation** — `bb spec` 1249 examples/0 failures, `bb features` 224 examples/2 failures (pre-existing, see below)/2 pending (pre-existing), `bb jvm-spec` 1249 examples/8 failures (pre-existing). New coverage: `spec/isaac/config/env_spec.clj` (lock-dotenv!/dotenv-snapshot contract), `spec/isaac/config/mutate_spec.clj` (validate-plan snapshot pass-through + the discord-shaped red/green regression, both with and without `.env`), `features/cli/config_set_dotenv.feature` (CLI-level red/green + the no-.env/warns-not-refuses companion scenario).

**isaac-http** — `bb ci` 195+111 examples/0 failures. New coverage: `spec/isaac/http/auth_cli_spec.clj` (mint!'s `:error` string is one `<path>: <message>` line per error, red before / green after), `spec/isaac/http/cli_spec.clj` (full CLI pipeline prints the multi-line string verbatim to stderr, no wrapping). Manifest bumped 0.1.23 → 0.1.24.

### Pre-existing red (not mine — confirmed via `git stash` back to origin/main HEAD before pushing)

- `features/cli/modules_pins.feature`: 2 failures, `Unable to fetch .../.gitlibs/_repos/file/REL/fixture-agent ... does not appear to be a git repository` — a stale **global** `~/.gitlibs` cache entry pointing at `isaac-foundation-isaac-89q1` (a different, now-gone bean worktree). Same failure with my changes stashed out. Did not touch the shared `~/.gitlibs` cache to avoid disturbing concurrent sibling sessions.
- `bb jvm-spec`: 8 failures, all `AbstractMethodError`/`IllegalArgumentException` on `isaac.module.protocol` defrecord/extend-protocol lifecycle hooks (`on-load`/`on-unload`) — the known bb-vs-JVM protocol-method gap (isaac-jf80 territory). Same 8 failures with my changes stashed out.
- 2 pending scenarios in `features/module/berth_registration_spec.clj` ("not yet implemented") — pre-existing, untouched.
