---
# isaac-c7e7
title: "Default tool exec to session cwd"
status: draft
type: bug
priority: high
tags:
    - "deferred"
created_at: 2026-05-03T17:19:41Z
updated_at: 2026-05-03T17:19:59Z
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

