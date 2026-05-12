---
# isaac-qpb
title: "Introduce Channel protocol behind chat flow"
status: completed
type: feature
priority: normal
created_at: 2026-04-10T22:21:19Z
updated_at: 2026-04-10T22:50:45Z
---

## Description

Extract presentation logic from process-user-input! into a Channel protocol so multiple renderings (CLI terminal, ACP notifications, memory recording, future Discord/Slack/web) share the same core chat flow.

## Problem

process-user-input! in src/isaac/cli/chat.clj directly calls println, flush, and a stdout-printing streaming callback. This couples orchestration to the CLI TUI. ACP's session-prompt-handler duplicates the flow in src/isaac/acp/server.clj to avoid the println calls — which means ACP sessions do NOT use compaction, token tracking, and other chat flow features.

## Solution

Define an isaac.channel/Channel protocol:

  (defprotocol Channel
    (on-turn-start  [ch session-key input])
    (on-text-chunk  [ch session-key text])
    (on-tool-call   [ch session-key tool-call])
    (on-tool-result [ch session-key tool-call result])
    (on-turn-end    [ch session-key result])
    (on-error       [ch session-key error]))

## Implementations (all in src/)

- isaac.channel.cli — current terminal behavior (println, flush, [compacting context...], [tool call: name])
- isaac.channel.memory — records events in an atom, no I/O. Primary test vehicle. Also usable by production code that wants to collect events.
- isaac.channel.acp — buffers session/update notifications in an atom, returned by ACP handler

## Refactor

- process-user-input! takes a :channel opt. Replace println/print-stream calls with channel method calls.
- CLI chat loop passes isaac.channel.cli/channel
- ACP session-prompt-handler becomes a thin wrapper: create memory-like ACP channel, call process-user-input!, collect buffered notifications, return {:result {:stopReason ...} :notifications @buffer}
- DELETE the duplicated tool-call loop and stream-once! in acp/server.clj

## Feature files

- features/channel/memory.feature (4 @wip scenarios)
- features/acp/prompt.feature (1 @wip scenario added for ACP compaction)

## New step definitions

- the user sends "{string}" on session {string} via memory channel — calls process-user-input! with a memory channel, captures events
- the memory channel has events matching: — horizontal table match against recorded event sequence

## Acceptance

Remove @wip and verify each scenario passes:
- bb features features/channel/memory.feature:19
- bb features features/channel/memory.feature:32
- bb features features/channel/memory.feature:48
- bb features features/channel/memory.feature:63
- bb features features/acp/prompt.feature:57

Additional verification:
- 'isaac chat' terminal behavior unchanged (manually verify streaming, compaction messages, tool call indicators print as before)
- acp/server.clj no longer duplicates the chat flow — stream-once! and tool-call-notifications helpers removed
- bb features and bb spec pass

