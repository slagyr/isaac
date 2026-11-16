---
# isaac-a5pu
title: 'Subagents: spawning from an isaac prompt process loses the child''s turn'
status: draft
type: bug
priority: normal
tags:
    - agent
created_at: 2026-11-16T14:44:41Z
updated_at: 2026-11-16T14:44:41Z
parent: isaac-4qxr
---

Repo: **isaac-agent** (spawn / turn submit) and possibly **isaac-foundation** (CLI process).

## Observed (zanebot, 2026-11-15)

A turn run with local `isaac prompt -c zane …` (in the short-lived CLI process, logging to cli.log) called `subagent__spawn`. The child session "astute-holly" was created, but its queued turn never ran and left no turn record: the queue it went to lived in the CLI process, which exited when the parent's turn ended. The same spawn through the server (hail) worked.

## Fix

Either the child's turn is durable and the server picks it up, or spawn refuses in a process with no running turn queue, with a clear tool error. Never a created session with a lost turn.

## Acceptance

Draft — scenario to come: spawning from a process without a running queue gives either a child that runs under the server or a tool error, and no orphaned child session.
