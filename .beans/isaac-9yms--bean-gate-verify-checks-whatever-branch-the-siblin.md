---
# isaac-9yms
title: 'Bean gate: verify checks whatever branch the sibling checkout is parked on, and the FAIL does not say which'
status: todo
type: task
priority: normal
tags:
    - process
    - beans
created_at: 2026-09-20T20:26:13Z
updated_at: 2026-09-20T20:26:13Z
parent: isaac-rmq6
---

Repo: **isaac** (beans tracker). Found dogfooding the gate on isaac-e20m
(scrapper@isaac-work-3, 2026-09-20).

## What happened

`bb bean-gate verify <id>` defaults each module to `../<repo>` at whatever
`HEAD` that checkout happens to be on. Shared sibling checkouts are routinely
parked on **another bean's branch** — `../isaac-foundation` was on
`bean/isaac-3kol`. The gate produced two confident failures that had nothing to
do with the bean under test:

    FAIL isaac-foundation features/cli/cli.feature: baselined block
         "Scenario: Top-level usage lists the version flag (isaac-2y86)"
         (baseline line 90) is missing
    FAIL isaac-foundation features/cli/init.feature: worker diff a1df8bc..b10519c
         edits a feature file the bean did not baseline

Both are artifacts of the parked branch: the scenario *is* on
`isaac-foundation` main (`3535286`), and `init.feature` belongs to isaac-3kol.
The same command with `--ref isaac-foundation=origin/main` → `PASS`, exit 0.

## Why it matters

A worker reading `FAIL … is missing` reasonably concludes the contract moved and
reaches for the revert-or-hail-plan path from `hail-bean-work-gate`. Nothing in
the output says "this is a different branch than you think": the pass line names
the ref (`@ HEAD <sha>` / `@ origin/main <sha>`) but the FAIL lines do not, and a
FAIL is where it matters. In CI the module is always a fresh clone, so this only
bites humans and agents — the ones who act on it.

## Change (pick one, planner's call)

1. **Loudest, cheapest:** print the resolved ref + sha per repo on *every*
   verdict, FAIL included — one `note:` line like
   `isaac-foundation: checking HEAD 8fce012 (branch bean/isaac-3kol)`.
2. **Safer default:** when the checkout's `HEAD` is not on `main`/`origin/main`
   and no `--ref` was given, add a warning naming the branch, and say in the
   message that `--ref <repo>=origin/main` is probably what was meant.
3. Document in `hail-bean-work-gate`: pass `--dir`/`--ref` whenever a sibling is
   parked on another bean's branch (a bean worktree is the normal case).

## Acceptance

- A FAIL verdict names, per repo, the ref and sha that were checked, and the
  branch name when `HEAD` is on one.
- Running the gate against a checkout parked on an unrelated `bean/<id>` branch
  reports that fact in a way a reader cannot miss.
- Spec coverage in `spec/isaac/bean_gate/` for the parked-branch case.
- `hail-bean-work-gate` gains the `--dir`/`--ref` note.
