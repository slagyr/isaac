---
# isaac-qq6a
title: 'Namespace restructure: isaac-handbook under its module id'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T14:12:24Z
updated_at: 2026-09-30T17:53:54Z
parent: isaac-vyqs
blocked_by:
    - isaac-on0o
---

Micah, 2026-09-30. **A module's code lives under its module id.** isaac-foundation → `isaac.foundation.*`, isaac-agent → `isaac.agent.*` (e.g. `isaac.session.frequencies` → `isaac.agent.frequencies`), isaac-claude-code (`:isaac.provider.claude-code`) → `isaac.provider.claude-code.*`, isaac-episodes (`:isaac.session.episodes`) → `isaac.session.episodes.*`, comm modules → `isaac.comm.<name>.*`, and so on. Source, specs, spec-support, step namespaces, manifest symbols (`:factory`, berth entries), bb tasks and docs all move together. Clean cutover: no alias namespaces.

## Order: inside-out (Micah)

1. isaac-foundation (requires nobody).
2. isaac-agent: bump to the new foundation, update its foundation requires, rename its own.
3. Every other module, in parallel: bump foundation + agent (this absorbs the pin sweep isaac-5x21), update requires, rename its own. A module that requires another leaf (gchat/gmail → google) goes after that leaf.

Each repo is touched once.

## Deploy freeze

An installed Isaac runs one foundation and one agent, so zanebot/yopp don't take the new foundation until every installed module has migrated. Each repo's main stays green on its own pins meanwhile.

## Every child bean also

- greps zanebot and yopp live config (read-only, `ssh zane@zanebot…` / `ssh yopp@yopp`) for namespace names in data (hook `:factory`, embedding `:namespace`, etc.) and lists required config edits in the bean;
- updates its handbook chapter and README where namespaces are named;
- is ungated (mechanical refactor): acceptance = full CI green on main, a grep showing no namespaces outside the module's id prefix in src/spec (list justified exceptions), planner verification.

## Landed on main

main-sha: isaac-handbook f7f7534

**Own namespaces already correct.** isaac-handbook's own `src/`, `spec/` namespaces were already `isaac.handbook.*` (module id `:isaac.handbook`) before this bean — no repo-owned namespace needed renaming. All work here was step 1: bumping to the new foundation/agent, and updating every foundation reference this repo makes.

**Pin bumps.** `deps.edn` + `bb.edn`: `io.github.slagyr/isaac-foundation`(`-spec`,`-test-support`) `fd91dd1` → `06d58b75bc52b3e118dc8e81569096de2532a0d4` (isaac-davq's landed sha), `io.github.slagyr/isaac-agent`(`-spec`) `7d3910f` → `123d71850b480dc0859886e1a4fa53e082c258f1` (isaac-on0o's landed sha), `io.github.slagyr/isaac-cron` (features test-dep, already migrated per the brief) `9c13a6a` → `c7607f49279072f32cd6e2bf959313bd8414e4f9` (its current main).

**Mapping — foundation references renamed (old → new), across src/, spec/, resources/isaac/handbook/handbook.md, README.md:**
- `isaac.cli.host` → `isaac.foundation.cli.host`
- `isaac.config.loader` → `isaac.foundation.config.loader`
- `isaac.config.mutate` → `isaac.foundation.config.mutate`
- `isaac.config.paths` → `isaac.foundation.config.paths`
- `isaac.config.schema-compose` → `isaac.foundation.config.schema-compose`
- `isaac.config.schema.resolve` → `isaac.foundation.config.schema.resolve`
- `isaac.config.cli.mutate-common` → `isaac.foundation.config.cli.mutate-common`
- `isaac.fs` → `isaac.foundation.fs`
- `isaac.logger` → `isaac.foundation.logger`
- `isaac.module.protocol` → `isaac.foundation.module.protocol`
- `isaac.module.berths` → `isaac.foundation.module.berths`
- `isaac.module.coords` → `isaac.foundation.module.coords`
- `isaac.module.discovery` → `isaac.foundation.module.discovery`
- `isaac.nexus` → `isaac.foundation.nexus`

No isaac-agent namespace was referenced anywhere in this repo (agent is only a `deps.edn`/`bb.edn` pin for classpath purposes), so no agent-side rename was needed.

**Left alone (justified — module-id/berth keywords, data not code namespaces):** `:isaac.config/schema` (manifest berth key), `:isaac.agent/tools` (manifest berth key), bare `isaac.foundation` and `isaac.agent` mentions in the handbook chapter/features prose and spec fixtures (module ids used as topic ids / module-index keys, e.g. `chapters_spec.clj`'s `{:isaac.foundation {} ...}` ordering fixture, `foundation_chapter.feature`'s `isaac.foundation#vocabulary` topic id). `isaac.foundation.handbook-chapter-spec` (a doc-comment pointer in `handbook_chapter_spec.clj` naming foundation's own already-renamed spec — confirmed that namespace exists at `isaac-foundation/spec/isaac/foundation/handbook_chapter_spec.clj`).

**No non-obvious/hand-fix traps found** (no class-name strings, no escaped-dot regexes, no lint-allowlist file paths, no keyword-mirrors-namespace cases) — this repo's foundation usage is all plain `:require` symbols and a couple of doc-comment prose mentions.

**Live-config greps (read-only, no edits needed).** zanebot (`ssh zane@zanebot.tail66e5f8.ts.net`) and yopp (`ssh yopp@yopp`) `~/.isaac/config`: no hits for `isaac.handbook`, `isaac.config.mutate`, `isaac.module.protocol`, `isaac.cli.host`, `isaac.fs`, `isaac.logger`, or `isaac.nexus` on either host. No config edits required.

**Test results.** `bb lint-cli-host`: ok. `bb spec`: 42/42, 0 failures. `bb features`: 22/22, 0 failures. `bb ci` (lint-cli-host + spec + features, using the real pinned git shas fetched fresh, not `:dev-local`): all green. No `bb jvm-spec` task exists in this repo.

**GitHub CI on main-sha f7f7534:** `CI Tests / verify` — green (1m46s, fresh checkout of isaac-foundation + isaac-agent mains, `bb ci`).

Full grep of tracked src/spec/resources/README/bb.edn/deps.edn/features for any remaining non-`isaac.handbook.*`/non-`isaac.foundation.*` `isaac.*` token: only module-id keywords (`isaac.foundation`, `isaac.agent`, `isaac.config` berth key) and `isaac.edn` (the config filename, not a namespace) remain — all justified as above.
