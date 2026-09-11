---
# isaac-r27u
title: 'Suite health (isaac-agent): bb features features/bridge/ episodes/ session/ times out on main'
status: draft
type: bug
priority: high
tags:
    - suite-health
created_at: 2026-09-11T16:54:38Z
updated_at: 2026-09-11T16:54:38Z
---

Ambient agent feature-gate: the named multi-directory invocation

    bb features features/bridge/ features/episodes/ features/session/

cannot satisfy GREEN on current `isaac-agent` main, independently of **isaac-jrj0**. Filed 2026-09-11 while adjudicating jrj0. **Do not reopen jrj0.**

## Observed (2026-09-11, scrapper@isaac-work-1)

- Clean detached worktree of `origin/main@4737cf4`: the exact command exits **124** at the built-in **180000ms** timeout and shows an `F` marker before timeout.
- `bean/isaac-jrj0` @ `f82c4d96` extends timeout to **300000ms** and still times out with failures.
- Isolated files that actually measure resume/suspend stay green: `features/bridge/suspend.feature features/session/resume_repair.feature features/session/boot.feature` → 10/0.

This is runner/shared-state suite infrastructure, not the component-contribution product.

Related: **isaac-jndk** (verify-gate process), **isaac-uxbt** (boot / compaction_template / episodes/live), **isaac-tx3j** (episodes/live :604), **isaac-1k85** (cli.feature:367).

## This bean owns

Make the three-directory invocation (or an equivalent unwrapped `clojure -M:features` of those trees) either:

1. exit 0 on current main, with failures named and owned if any remain, **or**
2. document that `bb features <dir> <dir> <dir>` is not a supported gate (wrapper timeout + shared-state) and that beans must name files, not trees.

Do **not** weaken scenario intent. Do **not** `@wip` without a dedicated owner.

## Acceptance

Reproduce on current `isaac-agent` main in a clean worktree. Record wall time, example counts, and every failing file:line. Then either green the trees or replace this bean's contract with a named-file policy plus owning beans for each remaining red.

The 180s `bb features` wrapper timeout under load is not by itself a product red; an `F` before timeout is.
