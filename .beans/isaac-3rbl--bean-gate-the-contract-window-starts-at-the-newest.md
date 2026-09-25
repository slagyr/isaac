---
# isaac-3rbl
title: 'bean-gate: the contract window starts at the newest planner baseline — a re-baseline re-cuts the contract'
status: completed
type: bug
priority: high
created_at: 2026-09-25T05:02:11Z
updated_at: 2026-09-25T05:02:59Z
---

## Why (Micah, 2026-09-25, on isaac-baf1)

The planner reworded an acceptance line while re-baselining. `contract-failures`
walks every consecutive bean version from the FIRST baselined one, so that edit
is a permanent FAIL — no re-baseline can clear it. Micah: "Can we not regate?"
Ruling: a planner re-baseline re-cuts the contract, so the append-only window
starts at the NEWEST baseline, not the first. Only the planner can open a window
(`baseline-author-failures` already refuses worker/verifier baseline commits).

## Design

`isaac.bean-gate.core/contract-failures`: the window begins at the newest
committed version that introduced a gate line (`feature-baseline:` /
`feature-blob:`) not present in its predecessor; pairs before it are ignored.
An uncommitted (working-tree) baseline line does not open a window.

## Acceptance (spec — this repo has no feature runner; ungated)

- [ ] A contract line edited and committed, then a planner re-baseline
  committed → PASS.
- [ ] The same edit with no later re-baseline → FAIL (existing spec).
- [ ] A baseline line only in the working tree after an edit → still FAIL.
- [ ] A worker-session re-baseline after an edit → FAIL (author check).
- [ ] Docs: work-bean-gate.md / AGENTS.md sentence: "correct a contract by
  re-baselining, never by editing a line in place".

## Ungated

isaac has speclj specs and no gherkin runner.
