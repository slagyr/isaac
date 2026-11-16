---
# isaac-35eh
title: 'Background exec and monitors: exec__run notifies the session when a command exits, or on each output line'
status: draft
type: feature
priority: normal
tags:
    - agent
    - tools
created_at: 2026-11-16T16:21:18Z
updated_at: 2026-11-16T16:21:18Z
parent: isaac-4qxr
blocked_by:
    - isaac-by8s
---

Repo: **isaac-agent** (`exec__run`, turn submit). Follows isaac-by8s (timers + no sleep-polling).

Micah, 2026-11-16, after comparing with Claude Code's tools: crews need to wait on things Isaac doesn't track — CI, a deploy, a log, a remote queue — without sleep-polling and without holding their turn.

## Model (Claude Code, for reference)

- **Background command** (`run_in_background`): returns at once; one notification when the command exits.
- **Monitor**: runs a script; every stdout line is a notification; ends when the script exits or at its timeout (max 30 min); lines within 200 ms batch into one notification; a monitor that floods is stopped.
- **Timer** (isaac-by8s) is the third leg: wake at a time, not on an event.

## Change (two steps, may split into two beans)

1. **Background `exec__run`** (`background: true`): returns a handle immediately; when the command exits, a turn on the same session carries the exit code and output tail (`origin {:kind :exec}`). If the session is mid-turn it joins (isaac-cgz9).
2. **Monitor mode** (`monitor: true`): each output line (after batching) becomes an event turn. Guard rails: required timeout with a cap, line batching window, max events per monitor (stop + notice when exceeded), list/stop by handle.

## Open questions

- Event cost: every event is a turn. Batch harder (e.g. seconds, not 200 ms)? Join running turns only?
- Restart: a process can't survive it. On boot, each monitor that was running delivers one "monitor ended: server restarted" turn rather than vanishing.
- Limits: per-session running background commands/monitors (default 4, like `:max-subagents`?).
- Guidance: add to the turn instructions only for crews with `exec__run`.

## Acceptance

Draft — scenarios to come.
