---
# isaac-2m0u
title: "Audit all tools for relative-path resolution against session cwd"
status: completed
type: bug
priority: normal
created_at: 2026-05-12T02:00:54Z
updated_at: 2026-05-12T04:44:33Z
---

## Description

The exec tool just had a fix where workdir="." (and any relative
workdir) was landing in the JVM daemon's process cwd instead of the
session's cwd. The fix (commit 930126b) resolves relative workdir
against the session cwd before passing to ProcessBuilder.

Same shape of bug almost certainly exists in other tools that
accept paths as arguments:

- read, write, edit — file path arguments
- grep, glob — path / pattern arguments
- file (whatever the consolidated file tool is)
- web_fetch — irrelevant (no filesystem path)
- session — n/a

Symptoms when broken: the LLM thinks "I'm in
/Users/foo/Projects/bar" (session.cwd), passes "src/main.clj" as
a tool arg, the tool reads from the JVM process cwd instead. On
zanebot this looked like the daemon's launch dir leaking into all
relative-path tool calls.

## Scope

Audit each file-touching tool. For each, verify:

1. Path arguments are resolved against the session's cwd (from
   session-store) when relative.
2. "." and "" map to the session cwd.
3. Absolute paths are honored as-is.
4. Outside-of-bounds paths produce a clear error (existing
   fs-bounds behavior).

For any tool that doesn't already do (1)-(3), apply the same
pattern as src/isaac/tool/exec.clj's resolve-exec-args. Add tests
mirroring the three new exec_spec tests:

- :workdir / :path "." resolves to session cwd
- empty path resolves to session cwd
- relative path resolves against session cwd

## Acceptance

- Every file-touching tool (read, write, edit, grep, glob, file)
  resolves relative paths against the session cwd.
- Each tool's spec file has tests pinning the three resolution
  cases.
- No regressions in features/tools/*.feature.

## Notes

- The session cwd lookup helper already exists in
  src/isaac/tool/exec.clj as session-workdir; consider promoting
  to a shared helper (perhaps in isaac.tool.fs-bounds or a new
  isaac.tool.cwd ns) to avoid duplication across adapters.

- This bead reflects an actual user incident on zanebot, not a
  hypothetical concern.

