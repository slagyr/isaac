---
# isaac-pqxn
title: 'Foreman live fixture: ungated round trip with one planned repair (isaac-q6fj)'
status: scrapped
type: task
priority: normal
created_at: 2026-10-28T07:08:34Z
updated_at: 2026-10-28T07:58:22Z
parent: isaac-q3u3
---

A deliberate test bean for isaac-q6fj's live acceptance: one ungated bean through the Foreman machine, with one planned repair. The deliverable is small but real.

## Ungated

orchestration has no feature runner for docs, so there is no gate. Ungated path: the worker ends with `event: unverified` and the verifier decides.

## Work

Add `isaac-beans/features/README.md` to slagyr/orchestration (on `bean/isaac-pqxn`, from origin/main). Three or four plain sentences:
- what the `.feature` files in this folder are (Foreman machine tests for `config/machines/isaac-bean-work.edn`)
- that they run with `isaac foreman test <file>` against an Isaac root that has the Foreman module, the crews the machine names, and the `isaac-worksites` pool
- that machine tests carry no instance data, so `{{data.*}}` renders empty in them

Don't touch README.md, bb.edn, install.sh or anything under config/: isaac-q6fj's branch changes those.

## Verifier: planned repair

This bean tests the repair path. **On the first verification**, don't land it: append a `## Repair 1` note asking the worker to add one sentence saying the tests use a fixed step vocabulary provided by Foreman. Then end with `event: repair fixture: planned first-pass repair (isaac-q6fj)`.

**On any later verification**, verify normally against the Acceptance below, which includes the Repair 1 sentence, and land it.

## Acceptance

- [ ] `isaac-beans/features/README.md` exists on orchestration main and covers the three points under Work, plus the Repair 1 sentence.
- [ ] No other orchestration files changed.
- [ ] `isaac foreman status isaac-bean-work isaac-pqxn` history shows working → verifying → working (repair) → verifying → landed.

## Reasons for Scrapping

2026-10-28. The run exposed isaac-mo02 (a stale alert turn's quiet end moved verifying → verify-stalled, so the verifier's correct `event: repair` was dropped) and the ungated-handoff skill gap (fixed on bean/isaac-q6fj 92e95dae). The instance could not get from verify-stalled back to working, so it was abandoned. Its bean state lives on isaac `bean/isaac-pqxn` (never on main); the orchestration `bean/isaac-pqxn` branch (847578d) holds the first-pass README. Both branches are fixture leftovers. The rerun is a fresh bean after Foreman 0.6.1 (fed1e6e) was deployed.
