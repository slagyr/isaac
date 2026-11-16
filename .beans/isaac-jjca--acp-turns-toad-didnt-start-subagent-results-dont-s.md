---
# isaac-jjca
title: 'ACP: turns Toad didn''t start (subagent results) don''t stream to an attached session'
status: todo
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

Scenario in `isaac-acp/features/comm/acp/background_turns.feature` (feature-level `@wip`; remove it when it passes):

- `bb features features/comm/acp/background_turns.feature:14` — after the client's own prompt ends, a turn submitted to the same session (origin `:subagent`) streams to the client: its input as `user_message_chunk`, its reply as `agent_message_chunk`

Run the whole file: `bb features features/comm/acp/background_turns.feature`.

## Prerequisites (do first, same bean)

isaac-acp pins agent `f05a1fe` and foundation `98de59a`, too old for the scenario's steps. Move every pin in `bb.edn` and `deps.edn` to agent `00ad7845e9fc7b590b2b317029ff1072638228e2` (0.1.96, deployed) and foundation `edc18481808a050f8c2fd657f2e966e6040fcfc3` (zanebot's keg), and add `"isaac.agent.turn.queue-steps"` to the `bb features` step globs. The planner verified the scenario fails for the right reason with exactly those changes (no notifications from the second turn).

feature-baseline: isaac-acp d8f1e1ba9f6b7b84f10021ce46adb4615ac46e69
feature-blob: isaac-acp features/comm/acp/background_turns.feature b3beb3b9b0385c94dc5f9cca09223003ad095c52
