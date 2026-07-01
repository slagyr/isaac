---
# isaac-q6et
title: 'Guard against config-read bypass: audit all projects + worker rule + validator check (code must use foundation''s resolving loader)'
status: completed
type: feature
priority: high
tags:
    - lint
    - foundation
    - config
created_at: 2026-06-30T20:11:18Z
updated_at: 2026-06-30T22:45:00Z
---

## Problem

Code outside foundation's config layer reads config **content** directly — `slurp` /
`edn/read-string` of `config/isaac.edn`, entity files, or `.env` — instead of going
through foundation's config loader. That loader is the only thing that applies:

- `${VAR}` secret resolution (dotenv / `resolve-env-values`)
- schema validation, defaults normalization
- entity-file merge + companion resolution

Bypassing it silently drops all of the above. The first confirmed instance is
**isaac-dyp7** (discord `effective-config` / `discord-slice-from-root` / `runtime-discord-cfg`
re-read `config/isaac.edn` raw → unresolved `${VAR}` token → Discord 401 → dead-letter).
This is a **class** of bug; we need to find the rest and stop it recurring.

## Why a naive grep is NOT enough (seed-sweep finding)

A line grep for `read-string|slurp` + `isaac.edn|config|.env` across all current module
SHAs **missed the known discord case**, because the config path is a local binding:
`(edn/read-string (fs/slurp fs* path))` where `path` is built elsewhere. Meanwhile the
lines that DO match are mostly **sanctioned** and must not be flagged:

- foundation `isaac.config.*` (the loader itself)
- `isaac.cli.registry` / `cli.clj` init scaffolding that **writes** `config/isaac.edn` (`write-edn!`)
- root-pointer resolution (`home.clj` reading `~/.config/isaac.edn` / `~/.isaac.edn` to *locate* the root, not read config values)

So the audit is code-tracing, and the check must distinguish "reads config **content** for
values" from "writes config" and "resolves the root pointer."

## Deliverables

1. **Audit all projects** — foundation, agent, server, acp, cron, hail, hooks, discord,
   imessage — for any code that reads config content outside the loader. Trace path
   construction (e.g. `paths/config-path`, `str state-dir "/config/isaac.edn"`,
   `fs/slurp` of entity/`.env` paths), not just literal filenames. Catalog each site
   (repo, file:line, what it reads, what resolution/validation it bypasses) and fix or
   file follow-ups. Known: isaac-dyp7 (discord).

2. **Sanctioned API** — confirm foundation exposes the *one* resolved-config accessor
   modules must use, **including a live / re-read variant** (the reason discord rolled its
   own raw read was isaac-vhyw's hot-reload-without-restart need). If that live-resolved
   read doesn't exist, add it. Document it as the only blessed way to read config.

3. **Worker guidance** — add a rule to the project conventions (CLAUDE.md / AGENTS.md /
   toolbox) so workers don't reintroduce it: *never `slurp`/`read-string` config files;
   always read config through foundation's resolved-config API.* State the few sanctioned
   exceptions (the loader, config writers, root-pointer resolution).

4. **Validator / lint check** — add an automated guard (`bb lint` / CI) that fails when a
   namespace outside `isaac.config.*` reads a config path (`config/isaac.edn`, entity
   dirs, `.env`) via `slurp`/`read-string`. Allow-list the sanctioned writers + pointer
   resolution. This is the enforcement that makes #3 stick. (A clj-kondo hook on the
   config-path helpers, or a custom check, since plain grep is insufficient — see above.)

## Related

- isaac-dyp7 — the discord instance (raw live read → unresolved `${VAR}` token → 401).
- isaac-vhyw — the live-reload need that motivated discord's bypass.


## Implementation notes (work-2)

### Sanctioned API (`isaac-foundation`)
- `isaac.config.api/load-resolved`, `resolved-config`, `resolved-slice` delegate to loader.

### Bypass fixes
| Repo | Site | Fix |
|------|------|-----|
| isaac-discord | `discord.clj` runtime/hot-reload paths | loader `load-config-result` only |
| isaac-foundation | `main.clj`, `launcher.clj`, `modules/cli.clj` | `config-api/load-resolved` |

`logs/cli.clj` no longer reads config (streams registry refactor on main).

### Lint + rule
- `bb config-bypass-lint` in foundation `src/` + wired into `bb ci`.
- `AGENTS.md` Config Read Discipline section.

### Audit (production `src/` lint clean)
foundation, discord, server, hooks — no violations. Other module repos scanned via shared lint script.

### Verification
- `bb spec` green: foundation (807), discord (69).
- `bb config-bypass-lint` ok on foundation + discord/server/hooks/agent src.


### Lint delivery fix (deps, not vendored copies)
- `isaac.foundation.config-bypass-lint` in `spec-support/src/` (single implementation).
- Exposed via `io.github.slagyr/isaac-foundation-test-support` (`:deps/root "spec-support"`).
- Module `bb.edn` depends on test-support coord; `bb config-bypass-lint` requires the ns.
- Foundation `:config-bypass-lint` deps alias for `clj -M:config-bypass-lint`.
- Test-support sha bumped to `44e824c` across modules after foundation push.

## Verifier review

Verified on current heads:

- `isaac-foundation` `44e824c6eded90301ed1aba8ed3948c66725ee43`
- `isaac-discord` `e914a6d2030d01878f377865a7e0ed8644de0fac`
- `isaac-agent` `153baa684c42c43bda46d5975e6f7f973a533568`
- `isaac-server` `dfa8767a440fc4c8b8bc6877ffd58ae20d028367`
- `isaac-acp` `1d10231442299d41de9781d9a3a2bdf2602ce33c`
- `isaac-cron` `cbb6c422d52a13eaed09b9b1b09edbb1d5daa12b`
- `isaac-hail` `d66f64697cc0e0dc91f8e36766414a635fb19072`
- `isaac-hooks` `14b78a91b9e832d90421dc4ce6b14c2daaea5e54`
- `isaac-imessage` `b993bf18a648f27721a5db3365252496ae1af102`

Proofs:

- `isaac-foundation`: `bb spec` -> `807 examples, 0 failures, 1424 assertions`
- `isaac-foundation`: `bb config-bypass-lint` -> `ok`
- `isaac-discord`: `bb config-bypass-lint` -> `ok`
- `isaac-agent`: `bb config-bypass-lint` -> `ok`
- `isaac-server`: `bb config-bypass-lint` -> `ok`
- `isaac-acp`: `bb config-bypass-lint` -> `ok`
- `isaac-cron`: `bb config-bypass-lint` -> `ok`
- `isaac-hail`: `bb config-bypass-lint` -> `ok`
- `isaac-hooks`: `bb config-bypass-lint` -> `ok`
- `isaac-imessage`: `bb config-bypass-lint` -> `ok`

Code-level sanity checks also match the bean:

- foundation exposes the sanctioned API in `src/isaac/config/api.clj`
- `isaac/AGENTS.md` now has a Config Read Discipline section that bans raw config reads and points workers at `isaac.config.api`
- Discord’s live config path now goes through `loader/load-config-result` in `src/isaac/comm/discord.clj`, and its spec explicitly proves `${VAR}` resolution through the foundation loader
