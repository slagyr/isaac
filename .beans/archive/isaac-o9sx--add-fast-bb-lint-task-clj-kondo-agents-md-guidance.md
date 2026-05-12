---
# isaac-o9sx
title: "Add fast 'bb lint' task (clj-kondo) + AGENTS.md guidance"
status: completed
type: task
priority: low
created_at: 2026-05-06T15:47:25Z
updated_at: 2026-05-07T19:30:55Z
---

## Description

Why: agents (Claude, Codex, others) sometimes burn significant time and tokens balancing parens after large edits. The current loop is edit -> bb spec -> compile error -> patch -> repeat. bb spec loads the whole project (~seconds) just to discover a paren is off. A fast clj-kondo-backed lint pass finishes in <300ms and gives line-pointed diagnostics agents can act on directly.

## Scope

- Add a 'bb lint' task in bb.edn, clj-kondo-backed.
  - Default target: 'src spec' (or whatever covers the project).
  - Pass-through file/dir args: 'bb lint src/isaac/foo.clj' lints just that file.
  - Use clj-kondo as a library (preferred) or shell out to the binary; whichever is cleaner in bb.
- Add a deps entry for clj-kondo if it isn't already on the classpath.
- Update AGENTS.md (under Testing Discipline or its own section) telling agents to run 'bb lint <file>' after editing Clojure files, before 'bb spec'. Cite it as the fast paren/syntax check.

## Why no pre-commit hook

Agents are already required to pass specs before committing. A pre-commit lint hook would catch nothing the spec gate doesn't already catch. Skipped intentionally.

## Acceptance

- 'bb lint' runs clj-kondo on the requested file/dir (or default scope) and finishes in well under a second per file.
- AGENTS.md includes guidance to use 'bb lint' as the fast pre-spec check.
- 'bb lint' returns a non-zero exit code on syntax errors (so agents can branch on success/failure).

## Acceptance Criteria

bb lint task exists, runs clj-kondo, fast (<300ms per file), exit code reflects success/failure; AGENTS.md guides agents to use it before bb spec

## Notes

Re-reviewed: acceptance is satisfied. The bead requires a fast clj-kondo-backed bb lint task with file/dir args, AGENTS.md guidance, and meaningful exit codes. It does not require the repo's default src+spec scope to be lint-clean. Verified: bb lint exists in bb.edn, AGENTS.md documents the pre-spec workflow, bb lint src/isaac/module/loader.clj runs clean and fast, and bb lint returns non-zero when findings are present.

