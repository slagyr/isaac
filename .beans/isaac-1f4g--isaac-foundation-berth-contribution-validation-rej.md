---
# isaac-1f4g
title: 'isaac-foundation: berth contribution validation rejects a :seq-of-strings contribution (apron conform on a non-map berth schema)'
status: completed
type: bug
priority: high
tags:
    - foundation
created_at: 2026-09-19T19:05:40Z
updated_at: 2026-09-19T19:07:31Z
---

Found 2026-09-19 installing isaac.google on yopp: `isaac config validate` reports
`module-index["isaac.google"].isaac.google/scopes - java.lang.Character cannot be cast to java.util.Map$Entry` for the contribution `["openid"]` against berth schema `{:type :seq :spec {:type :string}}`. Same for isaac.comm.gchat's scopes.

Cause: `isaac.module.berths/contribution-validation-errors` hands the berth's own schema to apron's `conform`, which only conforms MAP schemas at the top level. A :seq berth is walked element-by-element as map entries — a string's characters — so seq-of-strings fails while seq-of-maps (:isaac.http/route) passes by accident. Server boot is unaffected (the contribution registers fine); only validate is wrong.

Fix: conform the contribution inside a one-field map `{:contribution <schema>}` and unwrap the error paths. Specs added in spec/isaac/module/berths_spec.clj (seq of strings accepted; wrong-typed seq element reported; map berth error path preserved).

## Acceptance
    cd isaac-foundation && bb spec spec/isaac/module/berths_spec.clj && bb ci

## Handoff / resume
Planner fixed locally. branch: bean/berth-seq-validate @ 4c15b99 (base origin/main@df64bf1) — fast-forward from main. bb ci: 1057 spec examples green; features 197 with the only 2 failures being a stale local gitlibs fixture cache (cleared, modules_pins.feature 3/3 after). No version bump — foundation releases separately (yopp runs the main checkout and picks it up on pull).



## Landed on main (2026-09-19)

main-sha: isaac-foundation ba7fa5b453a4935043721b69a47cb3aac729fd80
