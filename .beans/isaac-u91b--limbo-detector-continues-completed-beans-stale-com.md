---
# isaac-u91b
title: 'Limbo detector continues COMPLETED beans: stale completion check (je45 bug)'
status: in-progress
type: bug
priority: high
created_at: 2026-07-12T23:38:00Z
updated_at: 2026-07-12T23:46:34Z
---

## Bug

isaac-je45's exemption says a turn that completes its bean is terminal (scenario 3) — but live on 2026-07-12, deliveries 78d5f4c4 and a72bffca kept receiving limbo continuations for isaac-l70j AFTER the bean was completed on beans main (worker turns even narrated 'holding — bean completed, no worker action'). Post-completion continuations burn full worker turns for nothing until the 3-cap dead-letters them.

## Hypothesis

The delivery worker's completion check reads a stale beans checkout (role-home clone or wherever it pulls) rather than fetching current state — the completion landed on origin but the checked clone was behind. Verify the actual read path and make the check authoritative: fetch/pull before deciding, or read the bean status via a fresh clone/ls-remote-backed mechanism. A slow check is fine (it runs once per turn end); a stale one defeats the exemption.

## Scenarios (worker writes; required coverage)

1. Turn ends with no hail; bean status is completed ON THE REMOTE but the local beans checkout is behind: NO continuation (the check must see the remote truth). This is the live counterexample — the existing scenario 3 passes because its fixture has no staleness.
2. Regression: genuinely incomplete bean still continues (je45 scenario 1 stays green).

## Observed churn cost

Each miss = a full worker turn (model + tools) narrating 'holding' until budget exhaustion.

## Planner correction (2026-07-12): real root cause is repo-key resolution, not pull staleness

`resolve-beans-dir` (isaac-hail beans_status.clj) looks up the delivery's `:data :bean-repo` — which band deliveries populate with the FULL GIT URL (`git@github.com:slagyr/isaac.git`) — against `[:hail-settings :beans-repos <key>]`, a short-key fallback (`= repo "isaac"`), or `:beans-root`. The URL matches none => dir nil => `bean-completed?` ALWAYS false => the exemption has never fired in production. je45's scenarios pass because the fixture step registers the SHORT key ("isaac") — fixture-reality divergence.

Rescope this bean:
1. `resolve-beans-dir` handles bean-repo URLS: derive the repo name from the URL tail (`isaac.git` -> `isaac`) for the short-key fallback AND consult :beans-repos with the raw URL (both).
2. Fixture step gains a URL variant so scenario 1 models production data (`bean-repo` = a git URL).
3. The staleness concern stands as written (refresh-beans-repo! already pulls — keep it and its coverage).
4. INTERIM (live now on zanebot, 2026-07-12): `:hail-settings {:beans-repos {"git@github.com:slagyr/isaac.git" "/Users/zane/agents/isaac/work-1/isaac"}}` added to isaac.edn — hot-reloaded, kills the l70j churn at the next turn end. Remove or keep as belt-and-braces once the URL derivation ships.
5. Bonus config-schema gap: `hail-settings.beans-repos` is not declared (config set rejects the path) — declare it (same fix shape as isaac-08r9).

This note resets any verify-fail count.
