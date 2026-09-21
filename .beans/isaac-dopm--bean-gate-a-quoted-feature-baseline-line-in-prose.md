---
# isaac-dopm
title: 'Bean gate: a quoted feature-baseline line in prose accidentally gates the bean'
status: completed
type: task
priority: high
tags:
    - process
    - beans
created_at: 2026-09-20T20:26:13Z
updated_at: 2026-09-21T17:33:58Z
parent: isaac-rmq6
---

Repo: **isaac** (beans tracker). Found dogfooding the gate on isaac-e20m
(scrapper@isaac-work-3, 2026-09-20).

## What happened

`isaac-e20m` is a process bean with no contract of its own. Its body *quotes*
another bean's baseline inside the planner-adjustment narrative, indented as a
code block:

        feature-baseline: isaac-foundation f031ff2dcdabe681f6c75ac8e367e44d3a7960b0
        feature-blob: isaac-foundation features/cli/cli.feature e2f95fd2… 90

`bb bean-gate verify isaac-e20m` read those quoted lines as **isaac-e20m's own
contract** and gated the bean on isaac-2y86's scenario. Expected exit 2 (not
gated); got a real gate run.

## Why it matters

- A planner or worker who cites a baseline while explaining something silently
  converts an ungated bean into a gated one. The worker then takes the gated
  close (land + complete) or chases a FAIL that belongs to a different bean.
- The reverse is worse: a narrative that quotes a *stale* baseline pins the bean
  to a commit nobody intended.
- `## Exceptions` and `## Acceptance…` have the same exposure — anything quoted
  under those headings is already append-only contract.

## Change

`isaac.bean-gate.bean` should only accept `feature-baseline:` / `feature-blob:`
lines that are **top-level body lines**: column 0, not inside an indented (4+
space) block and not inside a fenced ``` block. Same rule wherever the
append-only contract set is computed, so quoting a contract line in prose does
not freeze it either.

## Acceptance

- A bean whose only `feature-baseline:` lines are indented or fenced →
  `bb bean-gate verify <id>` exits **2** (`no feature-baseline: use the verify path`).
- A bean with a real column-0 baseline still gates exactly as today.
- Append-only history check ignores quoted/indented contract lines: a commit
  that edits prose containing a quoted `feature-blob:` line is not a violation.
- Spec coverage in `spec/isaac/bean_gate/` for both the indented and the fenced
  form.

## Implementation (2026-09-21, planner)

`isaac.bean-gate.bean` now honours a gate line only at **column 0**, outside
code fences. Indenting a `feature-baseline:` / `feature-blob:` line makes it a
markdown code block — prose quoting somebody else's contract — and the gate
reads it as documentation.

- `bean.clj` gains `top-level?`; `gate-lines` and the `:gate` branch of
  `contract-lines` both apply it, so a quoted line neither gates the bean nor
  gets frozen as append-only contract.
- `.github/workflows/bean-gate.yml` clone loop goes back to `/^feature-baseline:/`
  from the `/^[[:space:]]*…/` it was loosened to in `5aabd1fe`. That loosening
  existed only to match the gate's own loose parser; with one parser the
  workflow clones exactly what the gate will look for.
- `spec/isaac/bean_gate/bean_spec.clj` (new, 7 examples) covers the indented
  form, the fenced form, a quoted line not becoming contract, and a real
  column-0 baseline surviving alongside a quoted one.

Observed effect on the two beans that carried the defect:

    bb bean-gate verify isaac-e20m   → exit 2  "no feature-baseline: use the verify path"   (was: gated on isaac-2y86's scenario)
    bb bean-gate verify isaac-2y86   → exit 0  PASS (isaac-foundation @ main-sha 3535286)   (unchanged)

`bb ci`: 52 examples, 0 failures, 78 assertions.

## Landed on main (2026-09-21)

main-sha: isaac d4ba96e41a171306eff15d48b42ab6f2d8adea77

Squashed from `bean/isaac-dopm` (branch deleted local + remote). `bb ci` on
main after the squash: 52 examples, 0 failures, 78 assertions.
