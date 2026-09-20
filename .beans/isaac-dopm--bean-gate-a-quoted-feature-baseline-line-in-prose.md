---
# isaac-dopm
title: 'Bean gate: a quoted feature-baseline line in prose accidentally gates the bean'
status: todo
type: task
priority: high
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
