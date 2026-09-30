---
# isaac-yo8d
title: Move env_file.feature + dangling_md.feature from isaac-agent to isaac-foundation
status: in-progress
type: task
priority: normal
created_at: 2026-09-30T02:43:45Z
updated_at: 2026-09-30T02:46:25Z
---

## Ruling

Micah, 2026-09-29: isaac-agent's `features/config/` holds ~100 scenarios
for foundation-owned config-command behavior that should move to
foundation with Marigold fixtures; agent keeps only the scenarios that test
agent's own schema. This bean is the first, smallest, cleanest slice.

## Problem

`isaac-agent/features/config/env_file.feature` (3 scenarios: `${VAR}`
resolves from the isaac `.env` file, OS env takes precedence over it,
config loads when it's absent) and `dangling_md.feature` (2 scenarios: a
lone companion `.md` with no matching entity warns, a single-file `.md`
entity is not dangling) exercise mechanisms that are entirely
isaac-foundation's own:

- `${VAR}` substitution + `.env` layering: `isaac.config.env`, exercised
  in agent's version only through `:providers.anthropic.api-key` — no
  agent-specific behavior at all.
- Dangling-`.md` warnings: `isaac.config.entities/dangling-md-warnings`,
  a fully generic function that walks whatever `:entity-dir`s the loaded
  schema declares — agent's copy only ever exercised it through `:crew`.

Neither foundation's existing `features/cli/config_set_dotenv.feature`
(which tests a narrower thing: `.env` resolution during a STAGED `config
set` write, not plain load-time precedence/absence) nor any other existing
foundation feature covers what these two files cover.

## Wanted

Move both files to `isaac-foundation/features/cli/`, rewritten against
Marigold fixtures (module id `:marigold.cln1.widgets`, entity-dir
`"widgets"`, manifest-only — no `:factory`/`deps.edn`/`src`, since neither
scenario needs a running component). Delete both files from isaac-agent's
`features/config/`.

**Already drafted and dry-run green this session** (in a detached worktree
at `/Users/micahmartin/agents/isaac/plan/isaac-foundation-cleanup-draft`,
uncommitted):

- `features/cli/config_env_file.feature` — 3 scenarios, all passing
  (`bb features features/cli/config_env_file.feature` → 3 examples, 0
  failures). Uses `--reveal` + `stdin is: "REVEAL"` to check the actual
  resolved value (plain `config get` redacts any `${VAR}`-substituted
  value unconditionally, regardless of field name — this is NOT specific
  to fields that look like secrets; any resolved substitution is redacted
  by default, confirmed via `src/isaac/config/cli/common.clj`'s
  `redact-env-values`).
- `features/cli/config_dangling_md.feature` — 2 scenarios, both passing.
  **Care point for whoever lands this**: the "dangling" scenario's `.md`
  file must have NO frontmatter fence at all (plain body text only) — a
  `.md` WITH valid frontmatter becomes its own legitimate entity (that's
  how `crew/<id>.md` composition already works), not a dangling file. The
  original agent scenario's `ghost.md` was plain text for exactly this
  reason; an earlier draft this session mistakenly gave it frontmatter and
  the scenario passed for the wrong reason (nothing loaded) until this was
  caught and fixed via the "not dangling" companion scenario's positive
  assertion (`the stdout contains "OK - config is valid"`, added
  specifically to make sure the fixture module was actually loading and
  not silently no-op'ing).

The worker should copy these two files from the draft worktree (or
regenerate from this bean body's description — the exact content is
already correct and dry-run verified; no need to redo the debugging) into
a fresh `bean/<id>` branch, then delete the two source files from
isaac-agent.

## Acceptance

- `isaac-foundation/features/cli/config_env_file.feature` and
  `config_dangling_md.feature` exist and pass in the full `bb features`
  suite (not just standalone).
- `isaac-agent/features/config/env_file.feature` and `dangling_md.feature`
  are deleted; isaac-agent's full feature suite still passes.
- Feature titles reference `isaac-????` placeholder bean id — replace with
  this bean's real id when baselined.

## Likely repo scope

`isaac-foundation` (add 2 feature files), `isaac-agent` (delete 2 feature
files, confirm nothing else references them).

## Notes

- No production code changes — pure test-suite relocation. Not blocked by
  isaac-dnib, cleanup-beans 1-4, or the handbook beans (no file overlap).
  Can land first, independently, any time.
- Dry-run environment: `bb features <file>` inside a `git worktree add
  --detach <path> origin/main` off isaac-foundation (never switch branches
  in the shared `plan/isaac-foundation` checkout). The `timeout` shell
  command is not installed in this environment; `bb features`/`bb
  gherclj` already wrap their own JVM-level timeout (`bb.test-timeout`),
  so no `timeout` wrapping is needed or possible.

feature-baseline: isaac-foundation fcbf65ee21eebd0798177b55c309b5692405faba
feature-blob: isaac-foundation features/cli/config_env_file.feature 6e38eada14153c02406f9ce94d4c60fe3ef66fc0
feature-blob: isaac-foundation features/cli/config_dangling_md.feature acfd31aa849ab4147570359dabeba2ea0da359ab
