---
# isaac-8sih
title: "Provider extensibility via isaac.api.provider (built-ins stay in core)"
status: completed
type: epic
priority: low
created_at: 2026-04-30T22:36:52Z
updated_at: 2026-05-05T23:27:37Z
---

## Description

Why: the original framing of this epic — 'move every built-in provider to modules/' — was based on the unstated premise that everything in Isaac should be a module. That premise didn't survive scrutiny. Comms-as-modules (Discord, telly) makes sense because comms are optional and users may want to disable them. Providers are foundational; you can't run Isaac without one, modularization doesn't add user-visible benefit, and forcing built-ins into module subprojects adds boot complexity without payoff.

The legitimate goal is **provider registration extensibility** — third-party providers should be able to register the same way built-ins do. That's an API surface concern, not a layout concern.

## Revised scope

- isaac.api.provider (new): public surface for provider registration. Mirrors isaac.api.registry/lifecycle/logger pattern. Re-exports register-provider!, lookup, etc.
- Built-in providers (anthropic, ollama, openai-compat, grover, claude-sdk) STAY in src/isaac/llm/. They call isaac.api.provider at startup the way they call isaac.comm.registry today.
- Third-party / experimental providers can ship as modules. Their :entry namespace calls isaac.api.provider/register-provider! the same way. Module activation drives the registration timing.
- The ollama pilot under modules/isaac.provider.ollama/ is reverted — ollama returns to src/isaac/llm/ollama.clj. It was a pilot for a direction we're not taking.

## Supersedes the original plan

- The original sub-pieces (P3.1–P3.7) listed in the prior description are obsolete. No per-provider migration; built-ins stay where they live.
- isaac-mx1d (third-party module support) is unaffected — third-party providers, comms, tools all flow through the module system once isaac.api.provider lands. They just don't displace the built-ins.

## Next concrete work (file as separate beads)

- Revert ollama from modules/isaac.provider.ollama/ back to src/isaac/llm/ollama.clj. Drop the modules/ subproject layout for ollama.
- Establish isaac.api.provider surface (parallel to isaac-5i8v's registry/lifecycle/logger work).

## Acceptance for THIS epic

- All 5 built-in providers live in src/isaac/llm/ (ollama returned to core).
- isaac.api.provider exists and is the public registration surface.
- A third-party module can register a provider by calling isaac.api.provider/register-provider! from its :entry namespace.
- No regression in existing provider behavior.

## Notes

All 5 built-in providers + provider_test now register via isaac.api.provider/register-provider!. Module manifests renamed to isaac-manifest.edn. isaac.api.provider is the single public registration surface. 1266 specs + 491 features green.

