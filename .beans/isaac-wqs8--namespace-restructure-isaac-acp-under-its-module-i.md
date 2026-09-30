---
# isaac-wqs8
title: 'Namespace restructure: isaac-acp under its module id'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T22:24:14Z
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

main-sha: isaac-acp 07f9fb8

**Mapping.** isaac-acp's own code already lived almost entirely under `isaac.comm.acp.*`. Renamed the three stragglers under that prefix: `isaac.system` → `isaac.comm.acp.system` (src + all referencing requires), `isaac.manifest-spec` → `isaac.comm.acp.manifest-spec`, `isaac.config.schema-spec` → `isaac.comm.acp.config-schema-spec` (both spec-only, file moves + ns). Updated every foundation/agent require and the manifest `:factory` symbol (`isaac.module.protocol/module` → `isaac.foundation.module.protocol/module`) to the renamed `isaac.foundation.*`/`isaac.agent.*` names (isaac-davq/isaac-on0o) — ~40 distinct old→new pairs across src/spec, including `isaac.session.frequencies[-cli]` → `isaac.agent.frequencies[-cli]` (the explicit exception) and `isaac.util.jsonrpc` → `isaac.agent.util.jsonrpc` (easy to miss: it reads like a generic/shared utility from its own docstring, but it's actually agent's own namespace). Updated the episodes require for isaac-tt1q's rename: `isaac.session.policy.episodes` → `isaac.session.episodes.policy`. bb.edn/deps.edn step-globs bumped (`isaac.session.session-steps` → `isaac.agent.session.session-steps`, etc.) and a stale `isaac.config.*` doc-string in bb.edn's `config-bypass-lint` task fixed to `isaac.foundation.config.*` (matches foundation's actual `allowed-ns-prefixes`).

**Pins.** isaac-foundation `06d58b75bc52b3e118dc8e81569096de2532a0d4` (the exact sha isaac-agent's own main pins — not a later foundation main commit — for `bb pins` coherency), isaac-agent `f9530426d04b6f66f17ae51f6f9a1a697531b39d`, isaac-http `5dedcafbc0fcc199a72e1d378ba78d1db9305750`, isaac-episodes `907b42c0429a7949b2f79035b48be3a6231d1440`.

**Temporary blocker, resolved mid-bean.** isaac-episodes had not migrated when this bean started (bean isaac-tt1q, status `todo`); its own source still required the old foundation/agent namespace names, which broke `bb spec` (1 failure via foundation's builtin-index discovering episodes' manifest) and `bb features` (couldn't load `acp-steps.clj`'s `isaac.session.policy.episodes` require at all) once this repo's foundation/agent pins moved. Per the shared brief's rule ("stop before landing and report; don't land red"), work was paused and reported rather than landed red. isaac-tt1q landed shortly after (main 907b42c, `isaac.session.policy.episodes` → `isaac.session.episodes.policy`); resumed from the same worktree, bumped the episodes pin and the one require, and proceeded.

**Live-config greps (read-only, no edits made).** zanebot (`ssh zane@zanebot.tail66e5f8.ts.net`) and yopp (`ssh yopp@yopp`) `~/.isaac/config` both only match the unchanged `:isaac.comm.acp` module id (in `modules.edn`/`isaac.edn` and various dated backups). No config edits required on either host.

**Test results.** `bb ci` (config-bypass-lint + lint-cli-host + `bb spec` + `bb features`): green, both against the real classpath and with `HOME` pointed at a scratch dir (no config leakage observed either way). `bb spec`: 81/81. `bb features`: 70/70. `bb jvm-spec` (real JVM via `clojure -M:spec`, `HOME` isolated, `ISAAC_GIT=1` to force the pinned shas): 81/81. `bb jvm-features`: 70/70. No pre-existing-failure carryover to report — everything is clean.

Full grep of the tracked tree for any remaining pre-rename namespace token (excluding `isaac.session.episodes`/`isaac.gmail`/`isaac.google`/`isaac.comm.telly`/`isaac.agent`/`isaac.foundation` prose pointers to other modules, and `isaac.edn` the config filename): 0 unjustified hits. `src/`, `spec/` namespace prefixes: 100% `isaac.comm.acp.*`.

**GitHub CI on main-sha 07f9fb8:** `CI Tests / verify` — green (fresh checkout of isaac-foundation/isaac-agent/isaac-http mains, `bb ci` end-to-end).
