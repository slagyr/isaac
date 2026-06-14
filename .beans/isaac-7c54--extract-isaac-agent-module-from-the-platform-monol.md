---
# isaac-7c54
title: Extract isaac-agent module from the platform monolith
status: todo
type: epic
priority: high
created_at: 2026-06-14T14:53:10Z
updated_at: 2026-06-14T16:19:02Z
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
