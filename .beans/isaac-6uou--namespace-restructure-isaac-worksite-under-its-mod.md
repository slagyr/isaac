---
# isaac-6uou
title: 'Namespace restructure: isaac-worksite under its module id'
status: in-progress
type: task
priority: normal
created_at: 2026-09-30T14:12:24Z
updated_at: 2026-09-30T17:57:03Z
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

## Blocked — not landed (2026-09-30)

Rename work is done and pushed to `bean/isaac-6uou` on isaac-worksite (not
merged to main — CI is red for a reason outside this repo; see below).

**Pin bump.** deps.edn + bb.edn: foundation `9dd4bff` → `06d58b75bc52b3e118dc8e81569096de2532a0d4`; agent `92cd8a13` → `123d71850b480dc0859886e1a4fa53e082c258f1`; isaac-http `11e43014` → `56998543b3e5c40593d2a3ea97b16550e3731463` (isaac-http's current main, isaac-fkqz already landed). isaac-google was never a dependency of this repo — nothing to bump there.

**Mapping.** 11 files touched. This repo's own namespaces were already `isaac.worksite.*` except one flat outlier: `feature-steps/isaac/worksite_steps.clj` (ns `isaac.worksite-steps`) → moved to `feature-steps/isaac/worksite/worksite_steps.clj` (ns `isaac.worksite.worksite-steps` — kept the `worksite-steps` tail, matching the isaac-cron precedent, since gherclj's `isaac.**-steps` glob requires the last segment to literally end `-steps`; `isaac.worksite.steps` would silently stop matching and drop the step defs). Every foundation require got the module prefix: `isaac.cli.{api,host,registry}`, `isaac.config.{api,loader,root,schema-compose,schema.resolve,config-steps}`, `isaac.fs` (plus the `isaac.fs.RealFs` compiled-class check in `lock.clj`, the same class-name trap isaac-davq/isaac-on0o hit), `isaac.logger`, `isaac.module.{protocol,discovery,berths}`, `isaac.nexus` → `isaac.foundation.*`; `isaac.resource-pool` → `isaac.agent.resource-pool`. `resources/isaac-manifest.edn`'s `:factory isaac.module.protocol/module` → `isaac.foundation.module.protocol/module`; `bb.edn`'s `config-bypass-lint` doc string corrected to name `isaac.foundation.config.*` (the isaac-davq/on0o/isaac-hail precedent). Two prose-only docstring fixes in `spec/isaac/worksite/handbook_chapter_spec.clj` (`isaac.module.discovery`, `isaac.module.berths` → `isaac.foundation.*`). `:isaac.agent/resource-pool-types`, `:isaac/cli`, `:isaac.config/check` left alone (berth/config keywords, data contracts). `resources/isaac/worksite/handbook.md` needed no edits — its `isaac.agent`/`isaac.foundation` chapter cross-references already named the real post-rename module ids.

**Live-config greps (read-only, no edits needed).** zanebot (`ssh zane@zanebot.tail66e5f8.ts.net`) and yopp (`ssh yopp@yopp`) `~/.isaac/config`: zero hits for `isaac.(cli|config|fs|module|nexus|resource-pool|logger|worksite-steps).` on either host. No config edits required.

**Test results.** `bb spec`: 21/21. `bb jvm-spec` (real JVM via `clojure -M:spec`): 21/21. `bb ci`/`bb jvm-features`: **1 of 8 examples fails, reproducibly**, in `features/worksite/lock.feature`'s "two members run two turns; a third waits and takes the first member released" — `session "harbor" has transcript matching:` times out after 30s (`isaac.agent.session.session-steps/await-turn!`). Confirmed **not pre-existing**: the identical scenario passes clean (8/8, ~3.6s) against pre-change main (old pins, old namespaces).

**Root cause (isaac-agent, not this repo).** `isaac.agent.session.session-steps/user-sends-on-session` (spec/isaac/agent/session/session_steps.clj, part of the pinned `isaac-agent-spec` git dep) changed its singular `:turn-future` bookkeeping between the old and new agent pin:
- **Old** (`92cd8a13`): a second concurrent "wait:true" send unconditionally overwrites `:turn-future` with the newer send's future (`(g/assoc! :turn-future turn-future)` inside the pending branch, no guard).
- **New** (`123d718`): `(when-not existing-turn-future (g/assoc! :turn-future turn-future))` — only sets `:turn-future` if nothing was already parked there.

In our scenario, `harbor` sends first (parks, `:turn-future` = harbor's future) and `jetty` sends second (also parks; under the new guard, `:turn-future` stays = harbor's future, never becomes jetty's). After "the turn ends on session jetty" completes and cleans up jetty's own per-session future, `:turn-future` (singular) is still harbor's — which never completes in this scenario (harbor is left permanently parked in Grover's wait-gate, on purpose, to keep leasing its pool member). `await-transcript-turn!` sees harbor's per-session future still registered and still `= (g/get :turn-future)`, so it calls `(await-turn!)`, which derefs harbor's future with a 30s timeout expecting it to finish — but it never will. This regressed a legitimate pattern (assert an *earlier*, still-intentionally-parked session's already-persisted user message after a *later* session's turn completes) that isaac-agent's own feature suite apparently doesn't exercise. No isaac-agent commit after `123d718` (current agent main) touches this file, so there's no later pin that fixes it.

This is not fixable from isaac-worksite: the offending code lives in isaac-agent's `spec-support`/`spec` (pulled in as the pinned `isaac-agent-spec` git dep), and every assertion of `harbor`'s (or any still-parked session's) transcript after a different session's real turn completes will hit the same 30s hang, regardless of how this repo's own `.feature` file is reordered — the scenario's design (one member leased-and-parked for the whole test, a second released mid-test, a third admitted from the queue) is exactly what `:worksite`/`:resource-pools` needs to test.

Per the coordinator brief's "don't land red" rule, **stopping here rather than landing**. Recommend a follow-up isaac-agent bean to restore the old overwrite behavior (or otherwise stop treating a still-parked *other* session's future as equivalent to `(g/get :turn-future)` in `await-transcript-turn!`), then re-run this bean's `bb jvm-features` to confirm green and land.

Rename work itself is complete and pushed: branch `bean/isaac-6uou` on isaac-worksite, commit `51e0da2` (not merged — do not land until the isaac-agent regression above is fixed).
