---
# isaac-vqx3
title: 'AGENTS.md: document worker test-discipline policy (hook + CI + bug-bead feedback)'
status: in-progress
type: task
priority: low
created_at: 2026-05-09T14:20:21Z
updated_at: 2026-05-13T20:06:23Z
---

## Description

After the pre-push hook and CI bead-on-failure land, AGENTS.md needs a section explaining the policy so workers know what to expect. The convention shifts from "please run tests before push" (advisory) to "tests run automatically on push; CI catches bypasses by filing bug beads under your name" (enforced).

## What to add

A "Test Discipline" subsection (under or near "Session Completion") that says:

> Tests run automatically on push via a pre-push git hook. The hook short-circuits on doc-only changes; on code/test changes it runs `bb verify` and rejects the push if anything is red.
>
> If you bypass the hook (`--no-verify` or hook not installed), CI on main runs the same suite and files a P1 bug bead assigned to you on failure. You'll see it next session via `bd ready`.
>
> One-time setup per checkout: `bb hooks:install` configures git to use the repo-tracked hooks at `.githooks/`.
>
> Implication: never push code/test changes without running `bb verify` (or letting the hook run it). The "Session Completion" mandatory-push step assumes the hook will run; if it doesn't, you've created beads-tracked debt.

## Tasks

1. Insert "Test Discipline" subsection in AGENTS.md, located near "Session Completion".
2. Update "Session Completion" mandatory steps to reference the hook (currently says "Run quality gates if code changed" — make it "the hook will run them; do not bypass").
3. Add a one-line setup mention: "On a fresh checkout: `bb hooks:install`."
4. Cross-reference from PLANNING.md's "Worker premature-close" trap — note the hook + CI as the new safety net.

## Acceptance

- AGENTS.md has a "Test Discipline" section explaining the hook + CI flow
- Session Completion section updated to reflect that tests run automatically via the hook
- bb hooks:install is mentioned in setup docs
- PLANNING.md's "Worker premature-close" trap mentions hook + CI as detection
- No breaking format changes to either file (existing structure preserved)

## Why this is its own bead

The hook bead and CI bead each ship their own tooling. This bead consolidates the policy text in AGENTS.md so workers reading the doc see one coherent story rather than picking it up piecewise from two implementation beads.

## Depends on

- The pre-push hook bead (provides the hook to reference)
- The CI bead-on-failure bead (provides the CI behavior to reference)

Land this AFTER both — otherwise AGENTS.md describes mechanisms that don't yet exist.

## Acceptance Criteria

AGENTS.md has a Test Discipline section describing the pre-push hook (auto-run on code changes, instant on doc-only), the CI bead-on-failure (P1 bug bead on red main pushes), and the one-time bb hooks:install setup; Session Completion section updated to reflect automatic test running; PLANNING.md's Worker premature-close trap references the hook + CI as the new detection layer; no breaking format changes.

## Notes

Verification failed: dependency isaac-3usy is currently reopened, so the documented CI bug-bead flow is not yet verified end-to-end. Also, AGENTS.md has the Test Discipline text, but ISAAC.md does not appear to contain the promised Worker premature-close hook+CI cross-reference noted as the PLANNING.md replacement.



## Verification failed

The current docs are coherent, but they do not match this bean's own acceptance text. The bean is specifically about hook + CI bug-bead feedback, but current AGENTS.md / ISAAC.md describe CI failing the run rather than filing a P1 bug bead, because `isaac-3usy` is now scrapped. Also, the bean still asks for a Session Completion section update, and AGENTS.md has no Session Completion section. Reopening so the documentation bean can be reconciled with the current project policy.
