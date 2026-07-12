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
