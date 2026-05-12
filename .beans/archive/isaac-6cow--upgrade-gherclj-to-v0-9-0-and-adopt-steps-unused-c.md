---
# isaac-6cow
title: "Upgrade gherclj to v0.9.0 and adopt steps/unused commands"
status: completed
type: task
priority: normal
created_at: 2026-04-24T17:14:55Z
updated_at: 2026-04-24T19:10:49Z
---

## Description

Bump io.github.slagyr/gherclj in bb.edn:9 from v0.8.0 → v0.9.0 (sha f85eedd68475e54009d21172efb0e0bc6c32a7c8). v0.9.0 ships three features directly useful to Isaac:

1. Optional docstrings on defgiven/defwhen/defthen. Stored in the step registry, surfaced in the steps catalog. Lets us add contract hints (side-effect surface, sync/async behavior, gotchas) next to the phrase.

2. 'gherclj steps' command — prints the registered step catalog (phrase, docstring, location). Replaces the idea of a bespoke 'bb steps' wrapper; we can thin-wrap it if we want a short alias.

3. 'gherclj unused' command — reports step definitions that no feature references. Supports -t tag filtering. Replaces the ad-hoc static analysis I used to find the current 25 dead steps (isaac-htv1).

Work in this bead:
- Update bb.edn:9 to the new :git/tag and :git/sha.
- Add two bb tasks:
  - bb steps    — wraps 'gherclj -f features -s isaac.features.steps.* steps'
  - bb unused   — wraps 'gherclj -f features -s isaac.features.steps.* unused'
- Smoke-test each (bb steps prints something, bb unused reports exactly the set isaac-htv1 lists).
- Update memory/AGENTS.md or PLANNING.md to point new agents at 'bb steps' before drafting scenarios.

Out of scope (follow-ups):
- Adding docstrings to existing steps — incremental, per touch.
- Wiring 'bb unused' into CI so new orphans are caught at merge time.

Acceptance:
1. bb.edn pinned to gherclj v0.9.0.
2. bb steps and bb unused tasks work against this repo.
3. bb features and bb spec still pass (no step invocations broke — v0.9.0 should be backward-compatible, verify).
4. Running bb unused before isaac-htv1 lands reports the same 25 steps (sanity check that the two analyses agree).

## Notes

Verification failed: acceptance item 4 is not met. The upgrade and tasks work: bb.edn is pinned to gherclj v0.9.0, bb steps prints the step catalog, bb spec passes, and bb features passes. However bb unused currently reports 28 unused steps, not the expected 25 from isaac-htv1's analysis. The current output says '180 of 208 registered steps are in use (28 unused)'. That mismatch means the sanity-check acceptance is not satisfied yet.

