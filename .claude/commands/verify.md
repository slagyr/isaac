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

## Steps

1. Run `bd list --status=unverified` to find beads awaiting verification
2. If none found, inform the user and stop
3. For each unverified bead (highest priority first):
   a. Run `bd show <id>` to read the description and acceptance criteria
   b. Identify the feature files referenced in the bead
   c. Run the acceptance checks (see below)
   d. Make a pass/fail judgment
   e. If **pass**: `bd close <id> --reason="Verified: acceptance criteria met"`
   f. If **fail**: `bd update <id> --status=open --notes="Verification failed: <reason>"`
4. Report a summary of results to the user

## Acceptance Checks

Run these in order. Stop on first failure.

### 1. Tests pass
- Run `bb spec` — all specs must pass
- Run `bb features` — all features must pass
- If the bead references specific feature files, run those individually too

### 2. @wip removed
- Check the bead's referenced feature files for remaining `@wip` tags
- If the acceptance criteria say "@wip removed", grep for it

### 3. Acceptance criteria met
- Read each acceptance criterion from the bead
- For each criterion, verify it is satisfied:
  - If it references a command, run it and check the output
  - If it references behavior, check the scenarios cover it
  - If it references code changes, read the relevant files

### 4. No regressions
- If `bb spec` or `bb features` showed failures unrelated to this bead,
  note them but don't fail the bead for pre-existing issues

## What NOT to do

- Do NOT modify code. You are read-only.
- Do NOT re-implement anything. If the work is wrong, reopen and explain.
- Do NOT close a bead you're unsure about. When in doubt, reopen with
  questions for the worker.

## Arguments

$ARGUMENTS - Optional: specific bead ID to verify instead of checking all unverified
