---
# isaac-7c54
title: Extract isaac-agent module from the platform monolith
status: todo
type: epic
priority: high
created_at: 2026-06-14T14:53:10Z
updated_at: 2026-06-14T19:00:31Z
---

Carve the agent turn-loop out of isaac/platform into a standalone
`/Users/micahmartin/agents/plan/isaac-agent` repo that depends only on
isaac-foundation — sibling to isaac-server (agent ⊥ server). Mirrors the
isaac-server extraction playbook.

## Agent set (foundation-only)
`isaac.api`, `isaac.charge`, `isaac.effort`, and the dirs `bridge/ comm/
llm/ tool/ slash/ drive/ crew/ prompt/ session/` — PLUS provider auth
(`llm/auth/*`). Boundary scan (2026-06-14): the agent set has ZERO requires
into hail/hooks/cron/server EXCEPT `isaac.tool.hail` → `isaac.hail.queue`,
which is hail's send-tool (built on agent's session/tool framework) and
stays platform-side with hail. So agent carves clean.

## Stays in platform (the remainder, depends on isaac-agent)
`hail/`, `hooks.clj`, `cron/` — forward edges into agent confirmed
(hail queue/router/delivery_worker, hooks, cron/service all require agent).

## Conventions (mirror isaac-server)
- foundation via `:local/root "../isaac-foundation"`.
- tests pull a `:deps/root` platform classpath that EXCLUDES the repo under
  extraction, to break the test cycle (server uses `platform`; agent needs a
  "platform-sans-agent" root).
- foundation-spec for shared gherkin steps; marigold + telly fixtures.
- server declares the `:isaac.server/route` berth; agent CONTRIBUTES routes
  as manifest data (inert without a host) — no code dep on server.

## Phases
- [ ] Phase 1 — boundary prep in isaac/platform: relocate `tool/hail.clj`
      ownership to hail; add an isaac-agent boundary spec (agent file-set
      requires only foundation + agent-internal). Green = gate.
- [ ] Phase 2 — populate isaac-agent: deps.edn (foundation local-root +
      agent externals, NO http-kit/clout), bb.edn, manifest (route/tool/comm/
      slash/llm-api/provider berths + isaac.api), src = agent set, spec +
      features + foundation-spec steps + fixtures. `bb ci` green standalone.
- [ ] Phase 3 — flip platform: delete agent set from isaac/platform; platform
      deps add isaac-agent; remainder (hail+hooks+cron) depends on agent;
      break platform↔agent test cycle with a platform-sans-agent deps-root.

## Verification
- isaac-agent `bb ci` green with NO platform on classpath (Phase 2).
- isaac/platform `bb ci` green post-flip via isaac-agent local-root (Phase 3).
- boundary spec green; grep agent set for requires into
  server/hail/hooks/cron returns nothing.

### Progress (2026-06-14)
- Phase 2 carve + skeleton committed (isaac-agent 4d486f1).
- Agent berths renamed :isaac.server/* -> :isaac.agent/* (tools, llm-api, slash-commands, provider, provider-template, comm). Manifest authored from the fat :isaac.server manifest. isaac.agent.module added. All agent nses compile vs foundation.
- TODO: external comm modules (discord/imessage/acp) contribute to :isaac.server/comm -> must rename to :isaac.agent/comm (folds into isaac-m4bi).
- TODO: spec/features carve + bb ci green standalone.

### Spec carve + classpath blocker (2026-06-14)
- specs/features carved; foundation-spec deps coord fixed (:deps/root only works for git coords — pointed :local/root at the subdir). Baseline: 1160 examples, 72 failures.
- BLOCKER: agent test classpath drags isaac.comm.telly -> isaac(platform) -> isaac-server -> isaac-server/modules/isaac.agent, a STALE stub manifest (:isaac.server/* contributions) that SHADOWS the real agent manifest in builtin-index. Causes ~50 of 72 failures (providers/schema/dispatch/cli/tools/slash all read the stub).
- Needs: (a) server agent drops/repoints isaac-server/modules/isaac.agent; (b) agent test fixtures must not transitively pull platform->server (agent ⊥ server). Decision pending.
- providers.clj module-id lookup fixed (correct once shadowing resolved).

### De-shadow + spec green progress (2026-06-14)
- Server agent's fix landed (stub removed, isaac-server off classpath): builtin-index clean (:isaac.agent + :isaac.foundation). Provider cluster cleared (13->1).
- Spec boundary violations resolved: dropped tool/hail_spec (hail's), removed dead [isaac.server.routes] requires, relocated configurator_spec/steps + reconciler.feature (host/integration reconcile tests, assert against server.app/registries).
- Now: 1151 examples, 59 failures.
- DOMINANT remaining cluster (~38: schema.term/bridge-dispatch/CLI-Config): FOUNDATION load-order bug. schema_compose checks fragment validations against a lexicon populated by a defonce in isaac.config.validation, and memoizes via last-composed*. Compose before config.validation loads freezes a broken schema. FIX BELONGS IN FOUNDATION (schema_compose should ensure the lexicon / not cache pre-lexicon). Flag for foundation agent.
- Remaining ~21 (slash registry, behavior funnel, mutate, tool registry, config resolve) untriaged.

### Down to 10 failures (2026-06-14)
- FOUNDATION lexicon bug fixed (isaac-foundation 2074263): extracted isaac.config.validation-lexicon leaf ns so schema-compose can load the lexicon before composing (broke the config.validation<->schema-compose cycle). Foundation suite still 5 pre-existing failures (Module-protocol, unrelated).
- REAL agent blocker found+fixed (isaac-agent 6ad683f): schema.root + slash/builtin read config/slash contributions from the :isaac.server MODULE id; the rename put them in :isaac.agent. -> 59 to 10 failures. The lexicon was a real latent bug but NOT the agent's blocker.
- Remaining 10: ~8 are the :isaac.server/*->:isaac.agent/* rename RIPPLE — comm/tool/slash/provider contributors (telly fixture :isaac.server/comm, marigold, + real discord/imessage/acp) still use the old berth keys, so agent gathers nothing. Fixtures are SHARED with foundation/server/platform (still :isaac.server/*), so this is a coordinated cross-repo rename (same family as isaac-m4bi). ~2 config.mutate may be downstream of fixture providers.
- DECISION NEEDED: propagate the berth rename to all contributors (cross-repo) vs revisit.

### AGENT SPEC SUITE GREEN (2026-06-14)
- isaac-agent: 1151 examples, 0 failures (isaac-agent 528d6e9). Phase 2 spec-complete.
- Path from 10->0: renamed :isaac.server/*->:isaac.agent/* berth keys in agent spec fixtures; vendored telly+kombucha test fixture modules into isaac-agent/modules/ (specs load them via user.dir/modules); carved tool-test/provider-test module fixtures.
- Cross-repo rename SWEPT (uncommitted) in: isaac/src, isaac/spec, isaac/modules, isaac/resources, isaac-discord/src, isaac-imessage/src (28 files, 94 refs). NOT yet verified/committed in those repos. Platform comm-berth DECL lives in isaac-server (server agent owns) — coordination.
- REMAINING: (a) 7 agent FEATURE failures (config validate, crew tools, 3x config hot-reload, AGENTS.md-from-cwd, tool-exec logging) — triage; (b) verify+commit the cross-repo rename in isaac/discord/imessage.

### Feature triage (2026-06-14) — 7 failures, 4 root causes
Spec suite GREEN; features 516/7 (+76 expected @wip pending). The 7 are real behavioral wiring, not carve gaps:
1. Crew tools resolve to #{} — 'isaac prompt hi' runs prompt-cli (calls builtin/register-all!) but the dispatched llm-request has no tools; builtin tools aren't reaching dispatch tool-resolution in agent context. (also affects tool-exec logging #7?)
2. Config hot-reload x3 — gherclj 'no entries to match against' (reload watcher/change-source not firing or a step-table setup gap).
3. Tool execution logging — :tool/start logged but :tool/result missing (tool exec not completing/logging).
4. Config-validate unknown-tool-refs (#1) + prompt AGENTS.md-from-cwd (#6) — individual.
Each needs focused per-feature work. Spec-green is the milestone checkpoint.
Cross-repo rename still swept-but-uncommitted in isaac/discord/imessage (needs verify+commit; platform comm-berth DECL in isaac-server).

### Cross-repo rename verified — deferred to isaac-m4bi (2026-06-14)
(a) verify+commit the cross-repo rename: the agent berths are declared ONLY in isaac-agent. The platform's agent-code (gatherers) is the duplicate deleted in Phase 3 (renaming it = throwaway); discord/imessage pin the platform not isaac-agent, so renaming their contributions orphans them. Reverted the premature platform/discord/imessage sweep. The real rename is now tracked in isaac-m4bi (coupled to the module repos depending on isaac-agent). isaac-agent stays green via its own vendored fixtures.
### (b) feature failures: delegated to a background agent (7 failures, 4 causes).

### bb ci GREEN — Phase 2 complete (2026-06-14)
isaac-agent: spec 1151/0 + features 516/0 (72 @wip pending). Standalone green.
- Took over the stalled background agent; reviewed its work (sound). Its harness fixes landed: prompt-tools last-request fallback (crew-tools), restored dropped 'config:' log-routing step (tool-exec logging), agent-local 'Isaac server is started' synchronous-reload stand-in (hot-reload trio), ensure-feature-fs run-order fix, cli.feature berth-name update.
- The 2 spec 'regressions' the agent left were actually from MY earlier platform revert (telly fixture flipped back to :isaac.server) — fixed by re-renaming the telly+kombucha fixture manifests (isaac cf6bc1ca).
- Last feature failure was a carve gap: AGENTS.md (read from user.dir by the 'load AGENTS.md from cwd' scenario) wasn't carried — added it.
- Commits: isaac-agent 7c2249d, isaac cf6bc1ca.
REMAINING: Phase 3 (flip platform to depend on isaac-agent + delete duplicate agent code); cross-repo contributor rename for discord/imessage/acp (isaac-m4bi).
