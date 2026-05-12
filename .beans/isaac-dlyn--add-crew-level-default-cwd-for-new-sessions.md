---
# isaac-dlyn
title: "Add crew-level default cwd for new sessions"
status: draft
type: feature
priority: high
tags:
    - "deferred"
created_at: 2026-05-03T17:19:52Z
updated_at: 2026-05-03T17:19:59Z
---

## Description

Crew config needs a default working directory so new sessions
start in the crew's workspace automatically.

## Current behavior

- crew config has :model, :soul, :tools
- no :cwd
- new session cwd is set ad hoc by entrypoint:
  - Discord uses state-dir
  - ACP falls back to startup user.dir

## Desired behavior

- add :cwd to crew config/schema
- when creating a new session, initialize session :cwd from crew
  default if present
- fall back to current behavior only when crew default cwd is
  absent

## Why it matters

- soul/workspace and working directory stop being unrelated
- tempest-style crews can start in their real workspace
- Discord, ACP, and other entrypoints behave consistently

