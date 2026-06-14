---
# isaac-ccxw
title: Modular, idempotent isaac init scaffolding via berth contributions
status: draft
type: feature
priority: high
created_at: 2026-06-14T15:54:35Z
updated_at: 2026-06-14T15:54:35Z
parent: isaac-brth
---

Foundation `isaac init` still scaffolds the full monolith starter tree
(crew, models, providers, cron, Ollama copy) and tells users to run
`isaac prompt` — commands and config tables that do not exist until
agent/server/cron modules are installed. On a foundation-only install
this is misleading. Worse, init is all-or-nothing: if
`<root>/config/isaac.edn` exists it exits 1 and creates nothing, so
re-running init after adding modules cannot fill in missing starter
files.

## Motivation

The modular split means foundation ships one command (`init`) but must
not pretend the platform is present. Each installed module should
own the starter config it needs. Init should be safe to run repeatedly:
create only files that are missing, never overwrite operator edits.

Observed on a fresh root at `/Users/micahmartin/Projects/isaac/root`:
init created agent/cron-shaped files while only foundation was on the
classpath, then suggested `isaac prompt -m "hello"` (unknown command).

## Design — `:isaac/init` berth

Declare a foundation berth (like `:isaac/cli`) for starter scaffolding.
Modules contribute entries; foundation walks discovered modules (builtin
+ `:modules` from config when present) and merges contributions.

Proposed contribution shape (manifest data only):

```clojure
:isaac/init {:files [{:path "config/crew/main.md" :content ...}
                      {:path "config/models/llama.edn" :edn {...}}]
             :next-steps ["isaac prompt -m \"hello\""]}  ; optional lines
```

- **Foundation** contributes minimal shell only, e.g.
  `config/isaac.edn` with `{:prefer-entity-files true}` (no
  agent-specific `:defaults`, no cron entities).
- **isaac-agent** (or platform agent module) contributes
  crew/models/providers defaults + Ollama starter + `:defaults` merge
  fragment for isaac.edn + `prompt` next-step.
- **isaac-cron** contributes `config/cron/heartbeat.md`.
- **isaac-server** may contribute a commented `:modules` template or
  post-init hint — not required for MVP.

Per-file factory optional later; start with pure manifest `:files` data
validated by a berth entry-schema (path relative to root, content as
string or edn map, format keyword).

## Idempotent init semantics

Replace the current gate (`config/isaac.edn` exists → exit 1) with
per-path rules:

| Path state | Action |
|------------|--------|
| missing | create with contributed content |
| exists | skip (log at :info or print "  (skipped) path — already exists") |
| exists but unreadable | error, exit non-zero |

- Exit 0 when at least one file was created OR everything already
  present (print "nothing to create").
- Exit 0 with summary listing **Created** vs **Skipped** separately.
- Never overwrite existing files or merge into existing isaac.edn in
  v1 (avoid silent clobber). Optional future bean for `--merge` flag.

Re-run after installing a new module: user runs `isaac init` again;
only that module's missing files appear.

## Next-steps output

Assemble the post-init "Then try:" section from:

1. `:next-steps` lines on `:isaac/init` contributions from **installed
   modules only** (module index at init time = builtins + config
   `:modules` if resolvable), AND
2. Only suggest commands actually registered in `:isaac/cli` after
   `process-manifest-berths!` (don't mention `prompt` if agent absent).

Foundation-only success message should mention `isaac --help` and how
to add modules — not Ollama/agent commands.

## Implementation phases

- [ ] **Phase 1 — idempotent file writer** in `isaac.cli.registry`:
      per-path skip; update `init-run` exit semantics; adjust
      `spec/isaac/cli_spec.clj` + `features/cli/init.feature`.
- [ ] **Phase 2 — `:isaac/init` berth declaration** in foundation
      manifest; loader processes contributions; move hardcoded
      `scaffold!` / `created-files` / `print-success!` out of registry
      into berth gather + walk.
- [ ] **Phase 3 — module-owned scaffolds**: foundation minimal;
      isaac-agent + isaac-cron (or server split modules) contribute
      their files; remove agent/cron paths from foundation.
- [ ] **Phase 4 — dynamic next-steps** filtered by registered CLI
      commands; foundation-only feature scenario proves no `prompt` hint.

## Acceptance

- `bb spec` + `bb features features/cli/init.feature` green in
  isaac-foundation.
- Foundation-only `isaac init` on empty root creates only foundation
  starter files (no `crew/`, `models/`, `providers/`, `cron/` unless
  those modules are on classpath / declared in `:modules`).
- Foundation-only success output does not mention `isaac prompt` or
  Ollama setup.
- With agent module installed, `isaac init` creates agent starter files
  and suggests `isaac prompt` (or agent's actual command name).
- Re-running `isaac init` when `config/isaac.edn` already exists:
  skips existing paths, creates newly available module files, exit 0.
- Existing file contents never change on re-init (spec + feature assert
  byte-identical before/after second run).

## Out of scope

- `isaac init --force` / overwrite mode.
- Merging `:modules` or `:defaults` into an existing isaac.edn.
- Scaffolding user secrets or auth.json.

## Notes

- Current implementation:
  `isaac-foundation/src/isaac/cli/registry.clj` (`scaffold!`,
  `init-run`, `print-success!`).
- Existing feature explicitly requires refuse-on-exists and monolith
  scaffold set — scenarios must be rewritten, not just @wip.
- Aligns with isaac-brth: foundation uses the same extension API as
  third-party modules; init is not a special-case monolith dump.
