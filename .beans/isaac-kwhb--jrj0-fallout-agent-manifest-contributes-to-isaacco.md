---
# isaac-kwhb
title: 'jrj0 fallout: agent manifest contributes to :isaac/component, which only foundation 0.1.25 declares — config CLI and every CLI-driven feature fail with ''berth not declared''; main undeployable'
status: in-progress
type: bug
priority: critical
created_at: 2026-09-11T18:47:12Z
updated_at: 2026-09-12T14:19:00Z
parent: isaac-jrj0
---

Repos: isaac-agent (config/checks + spec harness), possibly isaac-foundation. Since isaac-jrj0 (8481d6c) the agent manifest declares contributions to the :isaac/component berth (foundation 0.1.25 / isaac-vs6f). Effects, all measured 2026-09-11:

1. isaac-agent features/config/cli.feature: 0 failures at 0.1.67 (4737cf4), 12 at the jrj0 landing (8481d6c) and on main (e9cba64) — config get/set/unset/sources/validate scenarios; agent CI red on both landings (20 feature failures in bb ci).
2. isaac-episodes (209q) features: 78 examples, 67 failures — every 'isaac is run with …' step aborts with `invalid configuration: module-index["isaac.agent"][:isaac/component] berth not declared by any installed module`.
3. A local `isaac config validate` with agent main as a local module prints the same error, so the check fires wherever the module index does not carry foundation's manifest.
4. zanebot runs agent 0.1.67 + server 0.1.14 + the brew-keg foundation; Micah's session rolled server 0.1.15 / claude 0.1.10 back (foundation runner path skips the server's boot hooks), so foundation 0.1.25 is not on zanebot either. Agent main is therefore undeployable until that train lands.

## Required
- Decide the contract: berths declared by the running foundation (the platform, always installed) count as declared even when the module index only carries module manifests — either the config check consults foundation's manifest (available as a dep), or the test harness's module-index builder includes it. The check must not reject a valid installation.
- Scenario (@wip → green) in isaac-agent: a manifest contribution to a foundation-declared berth validates; an unknown berth still fails with the same message.
- Green: features/config/cli.feature 59/0; full bb spec && bb features; isaac-episodes features against the fixed agent.
- Note for the train: agent main + isaac-episodes ship only after foundation 0.1.25 and server 0.1.15 are live on zanebot (Micah's train); the registry is re-pinned to the deployed 0.1.67 meanwhile (0ae12d2e).

## Checkpoint (2026-09-12, scrapper@isaac-work-3)

Done: pushed `isaac-agent bean/isaac-kwhb@2be3ba5`. The acceptance scenarios prove foundation-declared `:isaac/component` validates and an unknown berth retains the established error (`2 examples, 0 failures`). The Marigold fixture index now imports the component declaration from the running foundation instead of duplicating it. The config CLI feature explicitly models separate CLI processes and bypasses both process-threaded config and the stale startup config cache during each in-process invocation; `features/config/cli.feature` is restored to `59 examples, 0 failures, 238 assertions`.

Current state: focused acceptance is green; full agent and cross-repo acceptance have not run yet.

Next: run full `bb spec` and `bb features`, then run `isaac-episodes` features against this agent branch. Resume at `spec/isaac/config/agent_steps.clj:12`. Exact next command: `ISAAC_TEST_TIMEOUT_MS=600000 bb spec` from `/Users/zane/agents/isaac/work-3/isaac-agent`.
