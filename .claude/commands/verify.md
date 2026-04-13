---
name: verify
description: Verify recently completed beads meet their acceptance criteria. Use when the user says "/verify".
user-invocable: true
---

# Verify Completed Beads

Review beads marked `unverified` by workers. Check that the work
actually meets the acceptance criteria. Close if good, reopen if not.

You are a **reviewer**, not the implementer. You have fresh eyes.
Be thorough but fair.

## Status Names

This command uses default status names (`unverified`, `open`, `closed`). Projects may use different conventions.

If the project uses different names, use those instead of the defaults throughout.

## Steps

1. Run `bd list --status=unverified` to find beads awaiting verification
2. If none found, inform the user and stop
3. For each unverified bead (highest priority first):
   a. Run `bd show <id>` to read the description and acceptance criteria
   b. Identify any feature files or test references in the bead
   c. Run the acceptance checks (see below)
   d. Make a pass/fail judgment
   e. If **pass**: `bd close <id>` then sync bead state (`bd sync` if available; otherwise use the equivalent `bd dolt` commands)
   f. If **fail**: `bd update <id> --status=open --notes="Verification failed: <reason>"`
4. Report a summary of results to the user

## Acceptance Checks

Run these in order. Stop on first failure.

### 1. Tests pass
- Run the project's unit test suite — all tests must pass
- If the bead references feature files, run those scenarios too
- If the project uses gherclj, use `file:line` selectors to run only the relevant scenarios

### 2. Acceptance criteria met
- Read each acceptance criterion from the bead
- For each criterion, verify it is satisfied:
  - If it references a command, run it and check the output
  - If it references behavior, check the scenarios cover it
  - If it references code changes, read the relevant files
- If the project uses gherclj and the criteria include "@wip removed", grep the feature files to confirm

### 3. No regressions
- If the test suite showed failures unrelated to this bead,
  note them but don't fail the bead for pre-existing issues

## What NOT to do

- Do NOT modify code. You are read-only.
- Do NOT re-implement anything. If the work is wrong, reopen and explain.
- Do NOT close a bead you're unsure about. When in doubt, reopen with
  questions for the worker.

## Arguments

$ARGUMENTS - Optional: one or more specific bead IDs to verify instead of checking all unverified
