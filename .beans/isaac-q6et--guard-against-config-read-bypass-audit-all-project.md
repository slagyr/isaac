---
# isaac-q6et
title: 'Guard against config-read bypass: audit all projects + worker rule + validator check (code must use foundation''s resolving loader)'
status: todo
type: feature
priority: high
tags:
    - config
    - lint
    - foundation
created_at: 2026-06-30T20:11:18Z
updated_at: 2026-06-30T20:11:18Z
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
