---
# isaac-w8tx
title: Restart drops bound hail deliveries as stale strays — three bean turns lost at the 2026-09-11 04:00Z boot (hails never die)
status: in-progress
type: bug
priority: critical
tags:
    - unverified
created_at: 2026-09-11T04:05:47Z
updated_at: 2026-09-12T14:39:29Z
---

Repo: isaac-hail (delivery worker + resume interplay; agent 0.1.63 / hail 0.1.17). At the 04:00:14Z restart on zanebot (agent 0.1.63 + hail 0.1.17 deploy) the delivery worker logged `:hail/stale-delivery-removed` for 70d4d9c5 (isaac-work-2, isaac-tic5), 7c6354f1 (tono-work-1, tono-bzg0) and a403c825 (isaac-work-1, isaac-udnm) one second after `server/started`. All three sessions still carry `turn.edn` markers naming those delivery ids; the records exist in hail/records only — not delivered, not failed, not deliveries. The turns were not resumed and no worker is bound since boot. Every earlier restart today (09:16Z, 12:16Z, 14:21Z, 15:11Z) re-bound the same kind of markers as `:hail/bound :attempts N`.

Mechanism (isaac-3tyl / isaac-7li9 branch in `tick!`): a delivery in deliveries/ whose id is referenced by a turn marker is dropped when the session is not in flight. On boot, resume rewrites the marker's delivery into deliveries/ and the first tick runs before the resumed turn marks the session in flight (or resume no longer marks it), so the legitimate resume is mistaken for a claim-crash stray. Something in 0.1.63 (yk0u) / hail 0.1.17 (jejt: cancelled markers archived) changed the ordering or the in-flight mark.

## Required
1. Scenario (@wip first) in isaac-hail: a hail-bound turn marker survives a restart — after resume, the delivery is re-bound (attempts+1), never removed as stale; and the 7li9 stray case (marker with no delivery file and no resume) still drops.
2. Fix the ordering: the stray check must not apply to a delivery that resume just requeued (mark it, or run resume's requeue after marking in-flight, or exclude ids resume wrote this boot).
3. Recovery of the three dropped deliveries is manual (planner re-sends); note them here when re-sent.

Standing rule (Micah): infrastructure failures defer with attention and auto-deliver on recovery; a restart must never lose a work order.

Recovery (2026-09-11 04:42Z): the three dropped deliveries were re-sent (tic5 → ebbbff24, udnm → 3f023087, tono-bzg0 → f9d312b5) and bound within seconds of the migration boot; that boot also re-bound a tono hail on attempt 1 (ae298514) with no :hail/stale-delivery-removed — so the drop is not every boot. Compare the 04:00Z boot (first boot on 0.1.63 + hail 0.1.17, sessions still flat) with this one (0.1.66, nested layout).

## Work checkpoint (2026-09-12, scrapper@isaac-work-1)

Done: added executable restart-resume acceptance coverage and focused unit coverage. `tick!` preserves resume artifacts for both crash-orphan markers (`delivery attempts == marker attempts + 1`) and suspended markers (`delivery attempts == marker attempts`), while same-attempt claim-crash strays with no resume evidence still delete. Resumed work remains queued until the old marker clears, then rebinds without a stale-removal log. Recovery deliveries were already re-sent as recorded above.

Final branch: `bean/isaac-w8tx@7b355926fea8fa411a3b33d4212dbad19e8c7c2e` (base `origin/main@731a0e6ba9d7c24cef50480f3895c830be974870`). Evidence after rebase: focused feature 4 examples / 20 assertions green; focused delivery-worker spec 31 examples / 88 assertions green; full spec 159 examples / 367 assertions green. Full features/CI remain red only on the same pre-existing band config/inheritance/template failures and two pending hail-search scenarios; an isolated `origin/main` full-feature run reproduces 14 failures, while this branch has 13 (the unrelated hail crew-tool scenario became green). No bean-scope regression is present. Next: verifier reviews `features/turn-marker-claim.feature:47` and `src/isaac/hail/delivery_worker.clj:502`.



## Landed on main (2026-09-12)

main-sha: isaac-hail e344919540b71792cbeaace048fd57964001cc53

Squash-landed from sibling checkout. Bean-tip tree matches main tree.

Verifier gates (perceptor@isaac-verify) on bean/isaac-w8tx@7b35592:
- bb features features/turn-marker-claim.feature: 4 examples, 0 failures, 20 assertions (@wip removed)
- bb spec spec/isaac/hail/delivery_worker_spec.clj: 31 examples, 0 failures, 88 assertions
- bb spec: 159 examples, 0 failures, 367 assertions
- One-time: stray 7li9 scenario still green; resume-requeued deliveries are not logged :hail/stale-delivery-removed

Full bb features: 149 examples, 14 failures, 2 pending on the bean; origin/main 148 examples, 14 failures, 2 pending. Same band-config/inheritance/template + hail-send CLI failures; extra bean example is the new restart-resume scenario (green). Not a bean-scope regression.
