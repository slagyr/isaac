---
# isaac-51fg
title: 'Namespace restructure: isaac-mcp under its module id'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T14:12:24Z
updated_at: 2026-09-30T22:28:56Z
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


## Work in progress — blocked on isaac-wqs8 (2026-09-30)

Namespace rename done and pushed to `bean/isaac-51fg`, **not merged to main**: `bb jvm-features` (and therefore `bb ci`) goes red for a real, expected cross-repo reason, not a mistake in this rename.

**Mapping.** All 8 own files moved: `isaac.mcp.client[-spec]` -> `isaac.tool.mcp.client[-spec]`, `isaac.mcp.module[-spec]` -> `isaac.tool.mcp.module[-spec]`, `isaac.mcp.runtime[-spec]` -> `isaac.tool.mcp.runtime[-spec]`, `isaac.mcp.handbook-chapter-spec` -> `isaac.tool.mcp.handbook-chapter-spec`, `isaac.mcp-steps` -> `isaac.tool.mcp.mcp-steps` (feature-steps, following the isaac-cron precedent of keeping the full original leaf name nested under the module package rather than leaving a bare "steps"). Handbook resource moved `resources/isaac/mcp/handbook.md` -> `resources/isaac/tool/mcp/handbook.md`; manifest's `:handbook` path, `:factory`, and `:isaac.agent/tool-providers` symbol all updated. README's two code-namespace mentions updated. Every foundation/agent require updated to the new names looked up in `../isaac-foundation`/`../isaac-agent`: `isaac.module.protocol` -> `isaac.foundation.module.protocol`, `isaac.config.loader` -> `isaac.foundation.config.loader`, `isaac.logger` -> `isaac.foundation.logger`, `isaac.reconfigurable` -> `isaac.foundation.reconfigurable`, `isaac.runner` -> `isaac.foundation.runner`, `isaac.nexus` -> `isaac.foundation.nexus`, `isaac.fs` -> `isaac.foundation.fs`, `isaac.spec-helper` -> `isaac.foundation.spec-helper`, `isaac.cli.registry` -> `isaac.foundation.cli.registry`, `isaac.config.schema-compose` -> `isaac.foundation.config.schema-compose`, `isaac.config.schema.resolve` -> `isaac.foundation.config.schema.resolve`, `isaac.module.discovery` -> `isaac.foundation.module.discovery`; `isaac.util.jsonrpc` -> `isaac.agent.util.jsonrpc`, `isaac.tool.registry` -> `isaac.agent.tool.registry` (agent's own — confirmed by owner-check against `../isaac-agent`, not this module's `isaac.tool.mcp`). `isaac.mcp.turns` (flagged in the dispatch note as agent's `isaac.agent.mcp.turns`) is not referenced anywhere in this repo — nothing to change there. `isaac.comm.acp.cli` (isaac-acp, unmigrated) left untouched — correct, it's a different leaf's own namespace. Two doc-string-only misses caught by grep, not by the rename itself (same trap class as isaac-davq/isaac-on0o's notes): `bb.edn`'s `config-bypass-lint` doc string named `isaac.config.*` (foundation's prefix, not this repo's own) — fixed to `isaac.foundation.config.*`; `handbook_chapter_spec.clj`'s doc comment named `isaac.module.berths` — fixed to `isaac.foundation.module.berths`. No class-name strings, escaped-dot regexes, or lint path-allowlists found in this repo (grepped for the specific traps davq/on0o hit).

**Pins.** foundation -> `06d58b75bc52b3e118dc8e81569096de2532a0d4` (exact, as required), agent -> `123d71850b480dc0859886e1a4fa53e082c258f1`, http -> `56998543b3e5c40593d2a3ea97b16550e3731463` (isaac-http main, migrated). isaac-acp pin left at `9c825880b1334035b5d9218ee4b8b4ace4878c14` — its own main hasn't migrated (isaac-wqs8 still `todo`), so per the brief's rule it's not bumped.

**Why it's red.** isaac-mcp's `:features` deps alias pulls in isaac-acp (to register the `isaac acp` CLI command that `mcp_steps.clj`'s `hosts.feature` needs). isaac-acp's own `isaac.comm.acp.cli` still `(:require [isaac.cli.api :as cli-api])` — an old-style foundation namespace. Now that this repo's foundation pin points at the post-isaac-davq main, `isaac.cli.api` no longer exists anywhere on the classpath (foundation only ships `isaac.foundation.cli.api`), so `clojure -M:features` fails to load with `FileNotFoundException ... isaac/cli/api.clj`. Confirmed this is new, not pre-existing: isaac-acp's pinned sha is unchanged by this bean; the break comes purely from bumping isaac-mcp's own foundation pin ahead of isaac-acp's migration. This is the exact "module that requires another leaf that hasn't migrated" case the brief calls out — stopping before landing per its instruction rather than merging red.

**Test results on `bean/isaac-51fg` (pushed, not merged).** `bb spec` (native): 45/45, 0 failures. `bb jvm-spec` (JVM): 45/45, 0 failures (one pre-existing unrelated WARNING about `resolve` being replaced in isaac.agent.resource-pool, not from this repo). `bb lint`: 0 errors, 0 warnings. `bb ci` / `bb jvm-features`: **red**, `FileNotFoundException` loading `isaac.comm.acp.cli` per above — not run to completion; scenario count not available.

**Live-config greps (read-only, no edits made).** Both zanebot (`ssh zane@zanebot.tail66e5f8.ts.net`) and yopp (`ssh yopp@yopp`) `~/.isaac/config` have zero matches for `isaac.mcp` in any form. No config edits required on either host.

**Left undone:** the squash-merge to main. Work is on `bean/isaac-51fg` (pushed to origin, commit `94eccfa`), local worktree `../isaac-mcp-isaac-51fg` still present. Re-run `bb ci` once isaac-wqs8 (isaac-acp's own namespace restructure) lands, then land this bean the normal way (squash to main, confirm CI, `--tag=unverified`).


## Landed on main (2026-09-30, resumed after isaac-wqs8)

isaac-acp migrated (main 07f9fb8, isaac.comm.acp.*) while this bean was blocked. Resumed in the same worktree: bumped acp pin to `07f9fb813e8f6b6e7b5f10776e30825796315ea4`, agent to `f9530426d04b6f66f17ae51f6f9a1a697531b39d`, http to `5dedcafbc0fcc199a72e1d378ba78d1db9305750` (foundation unchanged at `06d58b75bc52b3e118dc8e81569096de2532a0d4`). `isaac.comm.acp.cli` (the namespace this repo's `mcp_steps.clj` requires) kept its own name across isaac-acp's migration — only its internal requires moved to `isaac.foundation.*`/`isaac.agent.*` — so no change was needed on this repo's side beyond the pin bump.

Rebased on origin/main (no new commits there), squashed the pin bump into the original rename commit, pushed directly as `7d6ee59` (`git push origin 7d6ee59:main`; not denied by the classifier), fast-forwarded the shared `isaac-mcp` checkout, and confirmed GitHub CI Tests green on `main-sha 7d6ee59` (`bb ci` job, ~42s).

**main-sha: isaac-mcp 7d6ee595bd92b08d7370456bd95b00def28e1e75** (see `git -C isaac-mcp log -1 --format=%H` for the full sha; short form confirmed via `gh run watch`).

**Test results, HOME-isolated (`/tmp/isaac_scratch_home_51fg`):** `bb ci` (config-bypass-lint + `bb spec` 45/45 + `bb jvm-features` 13/13) green. `bb jvm-spec` 45/45 green. `bb lint` 0 errors/0 warnings. Only benign noise: pre-existing `WARNING: resolve/reset! already refers to...` var-shadow warnings from isaac-agent's own `resource-pool`/`drive.observer` and isaac-acp's `system` namespaces (not this repo's code).

Removed `isaac-wqs8` from `blocked_by` (isaac-acp has migrated) and tagged `unverified` for `/verify`. Worktree `../isaac-mcp-isaac-51fg` and branch `bean/isaac-51fg` (both local and the earlier-pushed `origin/bean/isaac-51fg`) still need cleanup — deleting next.
