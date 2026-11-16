---
# isaac-jjca
title: 'ACP: turns Toad didn''t start (subagent results) don''t stream to an attached session'
status: draft
type: feature
priority: normal
tags:
    - agent
created_at: 2026-11-16T14:44:41Z
updated_at: 2026-11-16T14:44:41Z
parent: isaac-4qxr
---

Repo: **isaac-acp**.

## Observed (2026-11-16, reading isaac-acp 3fdae54)

The ACP comm streams only turns the ACP client started (`run-prompt` in `comm/acp/server.clj`). A subagent result is a new turn on the parent session started by the server, with no ACP comm attached. So in Toad: the `subagent__spawn` tool call shows, but when the child finishes, the result turn and Zane's reply to it never appear. Only a session reload (`session/load` replays the transcript) shows them. Exception: if the parent is still mid-turn from a Toad prompt, the result joins that turn and streams normally.

## Fix

While a Toad client has the session attached, turns on that session that Toad didn't start (subagent results, and later timers and hails) stream to it as session updates, so Micah sees the result and Zane's response live.

## Acceptance

Draft — scenario to come: an attached ACP session receives the agent's message chunks for a `:subagent` turn that runs after the prompting turn ended.
