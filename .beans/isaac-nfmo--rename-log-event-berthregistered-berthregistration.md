---
# isaac-nfmo
title: Rename log event :berth/registered -> :berth/registration
status: completed
type: task
priority: normal
tags: []
created_at: 2026-06-21T02:31:44Z
updated_at: 2026-06-26T20:02:37Z
---

Follow-up to olj5: rename the per-entry boot log event `:berth/registered` to
`:berth/registration`. Cheap to do now — it only shipped in foundation v0.1.6
(today) and nothing depends on the name yet. The summary event
`:berth/registration-summary` already uses the noun form and stays unchanged.

## Files (GitHub heads; local /plan mirrors are stale)
- isaac-foundation `src/isaac/module/loader.clj` (~:686) — the **emit** site.
- isaac-foundation `spec/isaac/module/loader_spec.clj` — assertion.
- isaac-foundation `features/module/berth_registration.feature` — assertion.
- isaac-agent `features/module/slash_extension.feature` — assertion.
- isaac-agent `spec/isaac/slash/registry_spec.clj` — assertion.

Only foundation emits the event; agent's two files just assert it (olj5 routed
slash-command registration through the uniform berth event). So agent needs its
tests synced to the new name but has no runtime change.

## Acceptance
- No `:berth/registered` remains in either repo (src, specs, features).
- Boot logs `:berth/registration` per entry; `:berth/registration-summary`
  unchanged.
- `bb lint` + `bb spec` (and the affected features) green in BOTH repos,
  verified against fetched GitHub heads (not the stale /plan mirrors).

## Deploy
- Needs a foundation release to land on zanebot (the emit change). Fold into the
  next foundation release rather than cutting one just for this.
- Agent needs no release for the rename (it doesn't emit); only its tests change.



## Implemented (work-2, 2026-06-26)

- isaac-foundation @ 8a5015c: emit `:berth/registration` (was `:berth/registered`); loader_spec + berth_registration.feature updated
- isaac-agent: slash_extension.feature + registry_spec.clj synced; foundation SHA bumped to 8a5015c
- No `:berth/registered` remains in either repo
- foundation bb spec + berth_registration.feature green; agent registry_spec + slash_extension.feature green

## Verification

Verified on fetched GitHub heads: `isaac-foundation` `8a5015c` and `isaac-agent` `e9f9fbd`. Focused proofs are green in both repos:
- foundation: `bb spec spec/isaac/module/loader_spec.clj` -> `33 examples, 0 failures, 78 assertions`; `bb features features/module/berth_registration.feature` -> `3 examples, 0 failures, 4 assertions`
- agent: `bb spec spec/isaac/slash/registry_spec.clj` -> `15 examples, 0 failures, 17 assertions`; `bb features features/module/slash_extension.feature` -> `3 examples, 0 failures, 4 assertions`
The targeted heads also match the intended rename split: foundation emits `:berth/registration` and agent only updates assertions against that event name.
