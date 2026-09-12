---
# isaac-kwhb
title: 'jrj0 fallout: agent manifest contributes to :isaac/component, which only foundation 0.1.25 declares — config CLI and every CLI-driven feature fail with ''berth not declared''; main undeployable'
status: in-progress
type: bug
priority: critical
tags:
    - unverified
created_at: 2026-09-11T18:47:12Z
updated_at: 2026-09-12T16:18:56Z
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

## Verification-ready (2026-09-12, scrapper@isaac-work-3)

Implementation: `isaac-agent bean/isaac-kwhb@fa2c536510598230965c00e64bfb77835c257a33`, rebased on `origin/main@374ea9e92306f8ee68a115ad4e92c743e2f7b221`. The Marigold fixture imports foundation's `:isaac/component` declaration from the running foundation resource without requiring an installed runtime fs. The feature harness treats each in-process CLI invocation as a separate process, bypasses stale process/cache config, and captures the fresh CLI load result for subsequent config assertions. Added valid foundation-berth and unchanged unknown-berth scenarios.

Green evidence: focused berth `2 examples, 0 failures, 2 assertions`; config CLI `59/0/238`; config directory `101/0/346`; agent native specs `1596/0/3289`; agent features split across all feature files `242/0/615` and `511/0/1175` (the latter retains the suite's one established pending scenario). The monolithic native feature task exceeds its fixed 180s wrapper timeout on this machine; the complete split run is green.

Cross-repo: `isaac-episodes` main repinned temporarily to agent `fa2c536` and foundation `0.1.25` for acceptance. The original undeclared `:isaac/component` error is eliminated (from `78 examples, 67 failures` to `78 examples, 3 failures, 515 assertions`). The remaining three failures are unrelated existing harness/assertion failures: two embedding-validation stderr assertions and one recall-log ordering assertion.

Train constraint: agent main and episodes ship only after foundation `0.1.25` and server `0.1.15` are deployed; registry remains pinned to deployed agent `0.1.67` meanwhile.
