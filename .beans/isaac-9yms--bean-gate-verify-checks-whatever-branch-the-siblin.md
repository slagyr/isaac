---
# isaac-9yms
title: 'Bean gate: verify checks whatever branch the sibling checkout is parked on, and the FAIL does not say which'
status: in-progress
type: task
priority: normal
tags:
    - process
    - beans
created_at: 2026-09-20T20:26:13Z
updated_at: 2026-09-21T17:27:06Z
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

## Implementation (2026-09-21, planner)

Options 1, 2 and 3 all landed — the acceptance bullets require each of them.

- **Every verdict names the tree.** `checked-commits` carries the branch, so the
  `HEAD` mode reads `HEAD <sha> (branch <name>)`. `main.clj` now prints the
  `checked` list on **FAIL** as well as PASS; it was PASS-only, which is exactly
  backwards — the FAIL is where the reader acts on it. An explicit `--ref` names
  itself and is left alone.
- **A parked branch is called out.** `parked-branch-note` fires when no `--ref`
  was given and `HEAD` is on a branch that is neither `main` nor this bean's own
  `bean/<id>`, suggesting `--ref <repo>=origin/main`. A worker sitting on its
  own bean branch — the normal case — gets nothing, so the warning stays worth
  reading.
- **`hail-bean-work-gate`** gains a blockquote under "Close: run the gate":
  read the ref/branch on the verdict line before believing a FAIL.
- `git/current-branch` returns nil when detached, so a detached checkout gets
  the sha with no branch clause and no note.

Reproduced on the real case the bean describes, against a sibling worktree
parked on an unrelated bean branch:

    # before
    isaac-6doh bean-gate: FAIL (5)
      FAIL isaac-agent …: baselined block "Feature: Resume repair and comm staleness" … was changed

    # after
    note: isaac-agent: HEAD is on branch bean/isaac-siua, not this bean's — pass
          --ref isaac-agent=origin/main if that is not the tree you meant to check
    isaac-6doh bean-gate: FAIL (5) — isaac-agent @ HEAD 7ffd522 (branch bean/isaac-siua)

    # after, with the ref the reader meant
    isaac-6doh bean-gate: FAIL (1) — isaac-agent @ origin/main cabfdf2
      FAIL isaac-agent …: scenario "…(isaac-6doh)" still carries @wip

The five confident failures collapse to the one true one (isaac-6doh is still in
flight; its scenario has not had `@wip` removed yet).

`spec/isaac/bean_gate/ref_spec.clj` (new, 5 examples) covers the parked branch in
the verdict, the warning, silence on the bean's own branch, silence under
`--ref`, and an explicit ref naming itself. `bb ci`: 50 examples, 0 failures.
