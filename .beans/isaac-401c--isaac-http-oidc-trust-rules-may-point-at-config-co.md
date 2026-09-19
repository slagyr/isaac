---
# isaac-401c
title: 'isaac-http: OIDC trust rules may point at config (config refs for :issuer/:audience/:claims)'
status: completed
type: feature
priority: critical
tags:
    - http
    - security
created_at: 2026-09-19T18:47:35Z
updated_at: 2026-09-19T18:50:45Z
parent: isaac-gym1
blocking:
    - isaac-x37l
---

Child of isaac-gym1; the isaac-http half of isaac-x37l, split out so it lands first (isaac-google pins it).

A module's manifest needs to trust "the audience configured for this door" without carrying deployment values. So an :isaac.http/identity rule's :issuer, :audience and :claims values may be config refs — a vector path such as [:google :push :endpoint] — resolved against live config when a token is verified. A rule with an unresolved ref is inert (the JWT falls through to bearer-hash auth and refuses :unknown). `isaac http auth list` shows the resolved values. Berth schema widened (:issuer/:audience :any). Version 0.1.20.

Also: step header lines may carry several headers joined with "; " (`Authorization: Bearer x; X-Forwarded-For: 203.0.113.9`) so consumers can model forwarded pushes.

## Scenarios (features/server/oidc.feature, done)
- a trust rule's audience and claims can point at config, so the manifest never carries deployment values (200; auth list shows the resolved audience)
- a trust rule whose config ref is unset is inert and the JWT falls through as unknown (401 :unknown)
Unit: spec/isaac/http/oidc_spec.clj resolves refs / unresolved ref ⇒ absent.

## Acceptance
    cd isaac-server && bb ci     # 185 spec, 104 feature examples, 0 failures

## Handoff / resume
Planner finished locally (2026-09-19). branch: bean/isaac-x37l @ 39efb60 (base origin/main@60df196) in isaac-http — fast-forward from main. Note the branch is named for x37l; verify lands it as this bean. isaac-google's bean/isaac-x37l pins http at 39efb60 and is repinned by the planner to the landed main sha before x37l is handed to verify.



## Landed on main (2026-09-19)

main-sha: isaac-http 493416d8e6508190f72ce56f7a99128b6a4a63f6
