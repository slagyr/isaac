---
# isaac-dgwf
title: 'Namespace restructure: isaac-imessage under its module id'
status: completed
type: task
priority: normal
created_at: 2026-09-30T14:12:23Z
updated_at: 2026-09-30T17:37:01Z
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

main-sha: isaac-imessage 6d19667a1eb2f8e5e33f36f3c5654da34e2c76c7

**Pin bump.** bb.edn + deps.edn: isaac-foundation(-spec/-test-support)/marigold.bridge/marigold.longwave `9ab2527` → `06d58b75bc52b3e118dc8e81569096de2532a0d4`; isaac-agent(-spec) `b6eb475` → `123d71850b480dc0859886e1a4fa53e082c258f1`; isaac-http(-spec/-test-support) `689d368` → `56998543b3e5c40593d2a3ea97b16550e3731463` (isaac-http's current main, confirmed already migrated per isaac-fkqz).

**Mapping — own namespaces.** isaac-imessage's own src/spec namespaces (`isaac.comm.imessage`, `isaac.comm.imessage.imsg-client`, and every `*-spec`/`imessage-steps` under those) were **already** under the module's own id prefix (`:isaac.comm.imessage`) — nothing to rename there. One outlier found: `spec/isaac/server/imessage_app_spec.clj` declared `(ns isaac.http.imessage-app-spec ...)` — a leftover from an earlier "Rename HTTP namespace references" commit (cfcf804) that updated the ns form but never moved the file, so ns and path had been mismatched since. Renamed to `isaac.comm.imessage.http-app-spec` and moved to `spec/isaac/comm/imessage/http_app_spec.clj` (this repo's own spec belongs under its own module prefix, matching every other spec here), and updated `deps.edn`'s `:spec` main-opts file list accordingly.

**Mapping — upstream requires**, all in `src/isaac/comm/imessage.clj`, `src/isaac/comm/imessage/imsg_client.clj`, and the spec/-steps files, resolved by diffing against the post-rename `../isaac-foundation` and `../isaac-agent` source trees (both provide identically-shaped `isaac.{foundation,agent}.spec-helper`; followed isaac-http's own precedent of `isaac.foundation.spec-helper`):
isaac.api→isaac.agent.api; isaac.charge→isaac.agent.charge; isaac.comm.delivery.queue→isaac.agent.comm.delivery.queue; isaac.comm.delivery.worker→isaac.agent.comm.delivery.worker; isaac.comm.factory→isaac.agent.comm.factory; isaac.comm.protocol→isaac.agent.comm.protocol; isaac.comm.registry→isaac.agent.comm.registry; isaac.component.registry→isaac.foundation.component.registry; isaac.component.runtime→isaac.foundation.component.runtime; isaac.config.api/.change-source/.loader/.root/.schema-compose/.schema.resolve→isaac.foundation.config.*; isaac.configurator-steps→isaac.http.configurator-steps (isaac-http's own spec-support ns, not foundation/agent); isaac.fs→isaac.foundation.fs; isaac.llm.api.grover→isaac.agent.llm.api.grover; isaac.logger→isaac.foundation.logger; isaac.module.discovery/.loader/.protocol→isaac.foundation.module.*; isaac.nexus→isaac.foundation.nexus; isaac.reconfigurable→isaac.foundation.reconfigurable; isaac.scheduler.runtime→isaac.foundation.scheduler.runtime; isaac.session.store.memory/.spi→isaac.agent.session.store.*; isaac.spec-helper→isaac.foundation.spec-helper; isaac.step-tables→isaac.http.step-tables; isaac.util.jsonrpc→isaac.agent.util.jsonrpc (its own docstring already said "come from isaac.util.jsonrpc" — an agent namespace, not this repo's own, despite the misleadingly-generic name). Manifest `:factory isaac.module.protocol/module` → `isaac.foundation.module.protocol/module`.

**Docstring-only pointers fixed to match** (not blind substitution — read for intent first): `handbook_chapter_spec.clj`'s two prose mentions of `isaac.module.lifecycle`/`isaac.module.berths` (foundation internals it references as fact) and one of `isaac.module.discovery`; `bb.edn`'s `config-bypass-lint` doc string ("outside isaac.config.*") corrected to "isaac.foundation.config.*" to match the lint's actual `allowed-ns-prefixes` (the same trap isaac-davq found and fixed in foundation's own bb.edn); `PLAN.md`'s "Reuses message construction from `isaac.util.jsonrpc`" → `isaac.agent.util.jsonrpc`. Left alone (already correct): the handbook chapter's `isaac.agent`/`isaac.foundation` prose mentions (already the real new module prefixes), `imessage_steps.clj`'s docstring mention of `isaac.agent.module-steps` (already correct), the `:isaac.agent/comm` berth keyword and `:isaac.comm.imessage` module-id keyword throughout (data contracts, unchanged per the bean), and `isaac.comm.telly` in `deps.edn`'s `:features` alias (isaac-agent's own fixture-module id, not a code namespace).

**Live-config greps (read-only, no edits made).** Both zanebot (`ssh zane@zanebot.tail66e5f8.ts.net`) and yopp (`ssh yopp@yopp`) `~/.isaac/config` — grepped for every old upstream namespace token this repo used (isaac.api, isaac.comm.*, isaac.component.*, isaac.config.*, isaac.fs, isaac.logger, isaac.module.*, isaac.nexus, isaac.reconfigurable, isaac.scheduler.runtime, isaac.session.store.*, isaac.spec-helper, isaac.util.jsonrpc, isaac.step-tables, isaac.configurator-steps) — zero hits on either host. No config edits required.

**Test results.** No `bb pins` task exists in this repo (git/sha literals in bb.edn/deps.edn are the pin sites, same as isaac-agent). `bb config-bypass-lint`: ok. `bb spec`: 67/67. `bb jvm-spec` (real JVM via `clojure -M:spec`, honors the `:spec` alias's now-corrected file list including the renamed `http_app_spec.clj`): 76/76. `bb jvm-features` (`clojure -M:features`): 23/23. All runs isolated via a scratch `HOME` per the isaac-davq/on0o precedent. No pre-existing failures encountered; nothing needed reproducing against pre-change main.

**GitHub CI on main-sha 6d19667:** `CI Tests / verify` (`bb ci`, checking out isaac-foundation/isaac-agent/isaac-http main fresh) — green.

Full grep of the tracked tree for any remaining pre-rename namespace token outside `isaac.comm.imessage.*`/`isaac.foundation.*`/`isaac.agent.*`/`isaac.http.*`: 0 hits. All 9 src/spec namespaces are `isaac.comm.imessage.*`.

## Planner verification (2026-09-30)

Verified on 6d19667: all namespaces isaac.comm.imessage.*, CI green. Worktree/branch cleanup classifier-blocked; left for Micah.
