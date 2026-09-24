---
# isaac-p4oj
title: 'config set / auth mint refuse valid writes: the staging validation never sees <root>/.env, so every ${VAR} reference resolves to unset'
status: in-progress
type: bug
priority: critical
created_at: 2026-09-24T13:37:58Z
updated_at: 2026-09-24T13:51:39Z
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
