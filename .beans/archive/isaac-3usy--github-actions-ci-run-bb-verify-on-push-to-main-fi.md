---
# isaac-3usy
title: 'GitHub Actions CI: run bb verify on push to main; file bug bead on failure'
status: scrapped
type: feature
priority: low
created_at: 2026-05-09T14:19:59Z
updated_at: 2026-05-12T17:49:06Z
---

## Description

Detection layer for cases where the pre-push hook is bypassed (--no-verify) or not yet installed on a worker's checkout. Runs the test suite on every push to main; on failure, files a P1 bug bead assigned to the commit author rather than emailing.

The bug bead is THE notification channel — it lands in the offending worker's bd ready queue, they pick it up next session per the parallel-worker pull policy, they fix. The repo owner sees nothing in their inbox.

## Tasks

### 1. Workflow .github/workflows/verify.yml

Triggers on push to main. Sets up Babashka + Clojure. Runs:
- bb spec
- bb features --tags=~@wip

If either fails, the next step runs a script that files a bug bead.

### 2. Bead-creation script

When tests are red, the workflow:
- Captures the commit SHA, author email/login (from $GITHUB_ACTOR or commit metadata), and the failing test summary
- Calls `bd create --type=bug --priority=1 --assignee=<author> --title="CI red on <short-sha>: <summary>" --description="<test output excerpt + commit context>"`
- Pushes to dolt: `bd dolt push`

Implementation choice — bd available in CI:
- Simplest: install bd via the same release path workers use (binary fetch or `cargo install` or whatever the runner supports). Cache between runs.
- Alternative: use `gh api` to call a beads webhook endpoint if one exists. Less direct.

### 3. Smoke-test the bead-on-failure path

Push a deliberately-red commit on a feature branch first to verify the workflow path works. Then enable on main.

### 4. NO email or chat notification on failure

Default GitHub Actions emails on failure. Disable for this workflow OR explicitly suppress via workflow config so the repo owner is not paged.

## Acceptance

- .github/workflows/verify.yml exists and runs on push to main
- A red push creates a bd bug bead with type=bug, priority=1, title containing the short SHA, assignee set to the commit author, description including the failing test output excerpt
- The bug bead is pushed to dolt and visible via bd ready (or bd list --assignee=<author>)
- No email is sent to the repo owner on failure (verified by checking GitHub notification config)

## Out of scope

- CI on PRs (no PR flow today; workers push direct to main)
- Pre-merge gating (no PR review either)
- Multiple workflows for different test slices — one verify workflow is enough until granularity is needed

## Soft dependency

Pairs with the pre-push hook bead. CI is the safety net for cases where the hook didn't run; the hook is the fast path for cases where it did. Both should land for the policy to be coherent.

## Why no Gherkin scenarios

CI workflow is infrastructure outside the Clojure surface. Acceptance is the workflow file + manual smoke test.

## Acceptance Criteria

.github/workflows/verify.yml exists and triggers on push to main; runs bb spec + bb features --tags=~@wip; on failure, files a bd bug bead with priority=1, assignee=commit-author, title containing short SHA, description including failing-test summary; bug bead is pushed to dolt; no email is sent to the repo owner on failure.

## Notes

Verification failed: bb ci is green and the bug-bead script dry-run produces the expected general shape, but the workflow still does not match the bead's exact acceptance text. .github/workflows/verify.yml runs 'bb ci' under a step named 'Run bb verify' instead of the accepted 'bb spec + bb features --tags=~@wip' flow, which changes coverage semantics because bb.edn defines bb features to exclude @slow as well as @wip. I also could not verify the acceptance item 'no email is sent to the repo owner on failure (verified by checking GitHub notification config)' or the requested smoke-test path from the repo alone, so I am not confident closing this bead.
