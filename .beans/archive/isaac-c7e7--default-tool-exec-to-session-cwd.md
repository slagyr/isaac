---
# isaac-c7e7
title: Default tool exec to session cwd
status: completed
type: bug
priority: high
tags:
    - deferred
created_at: 2026-05-03T17:19:41Z
updated_at: 2026-05-16T19:00:52Z
---

## Description

exec should use the session's :cwd as its default workdir when
none is provided explicitly.

## Current behavior (inconsistent)

- Isaac tracks per-session cwd
- some tool behavior already respects session cwd
- but command execution still depends too much on startup/process
  cwd or explicit workdir passing

## Desired behavior

- if exec is called without workdir, run in session :cwd
- if workdir is provided, use it
- status/output should reflect the actual directory used

## Why it matters

- /cwd becomes trustworthy
- tool behavior matches user expectation
- crews working in project workspaces stop drifting into the wrong
  directory

## Summary of Changes

This bean was already implemented before the audit caught it. Closing as completed; no code changes required.

**Where it lives:**

- `src/isaac/tool/exec.clj:39-44` — `resolve-exec-args` applies `bounds/resolve-path` against the session's cwd
- `src/isaac/tool/fs_bounds.clj:67-69` — `resolve-path` rules: nil/blank/"." → session-cwd; relative → joined with session-cwd; absolute → as-is
- `bounds/session-workdir` (same file) reads the session's `:cwd` from the session store

**Spec coverage** (`spec/isaac/tool/exec_spec.clj`):

- `:38` — "respects workdir option" (explicit workdir passes through)
- `:50` — **"uses the session cwd as implicit workdir when none is provided"** (the primary ask)
- `:66` — "prefers explicit workdir over the session cwd" (precedence)

Behavior matches the desired-behavior section of this bean exactly. The bean was stale documentation.
