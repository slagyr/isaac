---
# isaac-w8tx
title: Restart drops bound hail deliveries as stale strays — three bean turns lost at the 2026-09-11 04:00Z boot (hails never die)
status: in-progress
type: bug
priority: critical
created_at: 2026-09-11T04:05:47Z
updated_at: 2026-09-12T14:07:25Z
---

Repo: isaac-hail (delivery worker + resume interplay; agent 0.1.63 / hail 0.1.17). At the 04:00:14Z restart on zanebot (agent 0.1.63 + hail 0.1.17 deploy) the delivery worker logged `:hail/stale-delivery-removed` for 70d4d9c5 (isaac-work-2, isaac-tic5), 7c6354f1 (tono-work-1, tono-bzg0) and a403c825 (isaac-work-1, isaac-udnm) one second after `server/started`. All three sessions still carry `turn.edn` markers naming those delivery ids; the records exist in hail/records only — not delivered, not failed, not deliveries. The turns were not resumed and no worker is bound since boot. Every earlier restart today (09:16Z, 12:16Z, 14:21Z, 15:11Z) re-bound the same kind of markers as `:hail/bound :attempts N`.

Mechanism (isaac-3tyl / isaac-7li9 branch in `tick!`): a delivery in deliveries/ whose id is referenced by a turn marker is dropped when the session is not in flight. On boot, resume rewrites the marker's delivery into deliveries/ and the first tick runs before the resumed turn marks the session in flight (or resume no longer marks it), so the legitimate resume is mistaken for a claim-crash stray. Something in 0.1.63 (yk0u) / hail 0.1.17 (jejt: cancelled markers archived) changed the ordering or the in-flight mark.

## Required
1. Scenario (@wip first) in isaac-hail: a hail-bound turn marker survives a restart — after resume, the delivery is re-bound (attempts+1), never removed as stale; and the 7li9 stray case (marker with no delivery file and no resume) still drops.
2. Fix the ordering: the stray check must not apply to a delivery that resume just requeued (mark it, or run resume's requeue after marking in-flight, or exclude ids resume wrote this boot).
3. Recovery of the three dropped deliveries is manual (planner re-sends); note them here when re-sent.

Standing rule (Micah): infrastructure failures defer with attention and auto-deliver on recovery; a restart must never lose a work order.

Recovery (2026-09-11 04:42Z): the three dropped deliveries were re-sent (tic5 → ebbbff24, udnm → 3f023087, tono-bzg0 → f9d312b5) and bound within seconds of the migration boot; that boot also re-bound a tono hail on attempt 1 (ae298514) with no :hail/stale-delivery-removed — so the drop is not every boot. Compare the 04:00Z boot (first boot on 0.1.63 + hail 0.1.17, sessions still flat) with this one (0.1.66, nested layout).
