---
# isaac-7c54
title: Extract isaac-agent module from the platform monolith
status: todo
type: epic
priority: high
created_at: 2026-06-14T14:53:10Z
updated_at: 2026-06-14T14:53:10Z
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
