---
# isaac-2y86
title: Top-level --help lists --version / -V
status: todo
type: task
priority: high
tags:
    - process
created_at: 2026-09-20T18:41:50Z
updated_at: 2026-09-20T20:20:04Z
---

Dogfood for isaac-e20m (Bean Gate cutover). One scenario: top-level `--help` lists `--version` / `-V`. Reuses existing CLI steps. New steps: none.

`--version` / `-V` / `version` already work (`isaac.cli.host` + `features/cli/version.feature`). Top-level `usage-text` currently lists `--help, -h` and not `--version, -V`.

## Scenario

isaac-foundation `features/cli/cli.feature` — committed `@wip` on module **main**:

- **Top-level usage lists the version flag** — `isaac --help` stdout contains `--version` and `-V`; exit 0.

## Acceptance

```
cd isaac-foundation && bb features features/cli/cli.feature:90
cd isaac-foundation && bb ci
```

Remove `@wip` when the usage line lands. Gated: worker lands this bean (`bb bean-gate verify`, squash, `completed`). No verify hail.

Do **not** recut `features/cli/version.feature`. Do **not** change version-string format.

feature-baseline: isaac-foundation f031ff2dcdabe681f6c75ac8e367e44d3a7960b0
feature-blob: isaac-foundation features/cli/cli.feature e2f95fd2d79b2363297b1ee7fc757c2abbcf3f22 90

## Re-dispatch 2026-09-20 (planner)

The claim at `c775c8d0` belongs to a turn that died in the prompt_too_long
outage (hail `f780219f`, dead-lettered on isaac-work-3 at 18:45Z) — no worker
has held this bean since. Status reset to todo.

Going out to the **qwen** crew (`qwen3-coder-next` on ollama-nightbird) as the
first trial of a non-frontier model on real bean work. Chosen for it because
the `@wip` scenario is already committed at
`features/cli/cli.feature:90` on isaac-foundation main, so this is a plain
red-to-green with the acceptance commands written above — no test design, no
cross-repo reasoning, and a result anyone can check in one glance.
