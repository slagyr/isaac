---
# isaac-e90
title: "Use MemFs in feature step definitions for faster, isolated ATs"
status: completed
type: task
priority: low
created_at: 2026-04-15T03:37:37Z
updated_at: 2026-04-17T17:46:30Z
---

## Description

The MemFs abstraction exists and all production code uses the implicit *fs* API. Feature step definitions still use real disk I/O via RealFs. Switching to MemFs will speed up the AT suite and eliminate filesystem side effects between scenarios.

## Plan

1. **Bind MemFs in feature lifecycle** — In the `empty-state` step (session.clj:109), create a fresh MemFs and bind `fs/*fs*` to it. Store the MemFs in gherclj context so it persists across steps within a scenario.

2. **Remove clean-dir!** — MemFs starts empty per scenario. No filesystem cleanup needed. Drop the `clean-dir!` helper and the before-all/after hooks that call it.

3. **Keep state-dir paths as-is** — Virtual paths like `target/test-state` work fine in MemFs. No path changes needed in feature files.

4. **Handle exec-tool** — exec spawns real processes via ProcessBuilder and cannot use MemFs. Two options:
   - Features that test exec stay on RealFs (bind back for those scenarios)
   - Or: exec-tool tests that verify file I/O write to a real temp dir, exec-tool tests that only check stdout/exit don't need fs at all
   ~13 of 59 features reference exec/command.

5. **Handle logger** — Logger appends to a file via fs/spit. In MemFs this just works. The log file path is virtual.

6. **ACP step definitions** — The ACP steps (acp.clj) create sessions and dispatch prompts. All go through storage which uses fs. Should work with MemFs unchanged.

## Estimated scope

- Modify 1 step definition file (session.clj) — bind MemFs in empty-state, remove clean-dir!
- Verify all ~46 non-exec features pass
- Tag exec-dependent features or add RealFs binding for those scenarios
- No feature file changes needed

## Acceptance Criteria

Feature step definitions use MemFs. bb features runs faster. No real disk I/O for session storage in tests.

## Design

Bind fs/*fs* to MemFs per scenario in the empty-state step. MemFs resets naturally because each scenario creates a new binding. exec-tool scenarios need special handling since ProcessBuilder cannot see MemFs.

## Notes

Additional scope — surfaced while drafting isaac-16v and isaac-4vt features:

1. Rename the state-dir step for clarity:
   Given an empty Isaac state directory "X" → Given an in-memory Isaac state directory "X"
   Workers were getting confused by paths like /work/project and attempting to create them on disk. Making memfs explicit in the step name fixes this.

2. The plan says 'Keep state-dir paths as-is — virtual paths like target/test-state work fine in MemFs'. This is now wrong. fs/assert-absolute! (added in isaac-rh2 era) rejects relative paths. The step must translate scenario names like "isaac-state" to absolute memfs paths (e.g. /isaac-state) internally. Scenarios read clean; implementation handles translation.

3. Update call sites in features/config/composition.feature and features/tools/filesystem_boundaries.feature — both use the new phrasing and rely on memfs being set up by the step.

