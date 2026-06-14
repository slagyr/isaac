---
# isaac-o2qz
title: Extract isaac-hooks from the platform monolith
status: completed
type: task
priority: normal
created_at: 2026-06-14T19:17:38Z
updated_at: 2026-06-14T19:17:38Z
---

## Summary of Changes
isaac-hooks extracted and green (isaac-hooks 80b50f3). bb ci: spec 17/0, features 0/0 (no hook features).

- src/isaac/hooks.clj carved (single source file, ~220 lines). Leaf module: nothing in the platform requires it; it depends on isaac-agent (bridge/charge/comm.null/session.*/config.runtime) + isaac-foundation (transitive via agent).
- Manifest (:isaac.hooks): declares the :isaac.hooks/hook berth (renamed from legacy :isaac.server/hook), contributes /hooks/* to server's :isaac.server/route berth (data only, inert without an HTTP host — no code dep on server), and the :hooks config-schema fragment.
- deps.edn: io.github.slagyr/isaac-agent :local/root (brings foundation transitively). bb.edn + CI mirror isaac-agent.
- spec/isaac/hooks_spec.clj carved (+ marigold fixture, :isaac.agent-keyed). Manifest verified: builtin-index has :isaac.hooks, hook berth declared, :hooks fragment composes into the root schema.

## Loose ends / notes
- Platform (isaac) still has src/isaac/hooks.clj + spec/isaac/hooks_spec.clj — delete when isaac drains (not now; avoid breaking the platform mid-transition).
- isaac-server has a DUPLICATE hooks.clj (server-agent leftover) — their repo's concern.
- marigold fixture re-carved into isaac-hooks/spec (duplication). An isaac-agent-spec coordinate (like isaac-foundation-spec) would let hooks/hail/cron reuse agent's spec fixtures instead of re-carving — worth creating before more agent-dependent modules extract.
- Same test-fixture loose end as agent: isaac-hooks deps on ../isaac/modules/marigold.* — must resolve before isaac empties.
