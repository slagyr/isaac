---
# isaac-x4j2
title: "Tool loop hits max iterations, stores empty assistant message — Toad shows nothing"
status: completed
type: bug
priority: normal
created_at: 2026-04-30T01:42:29Z
updated_at: 2026-05-05T23:30:09Z
---

## Description

When the LLM keeps requesting tool calls without producing text
content, the tool loop iterates until max-loops (10) is hit, then
exits.

## Current state (partial fix landed)

A worker has already shipped a synthetic fallback at
src/isaac/drive/turn.clj:304:

  \"I ran several tools but did not reach a conclusion before
   hitting the tool loop limit. Ask me to continue if you want
   me to keep digging.\"

This avoids the empty-message symptom (Toad showing nothing).
But the canned text generic-summarizes what happened without
surfacing anything the LLM actually learned during the
iterations. The user's original prompt still goes unanswered.

## Remaining work: let the LLM produce a real wrap-up

When the loop is about to give up, send one final LLM request
with explicit instruction to summarize and tools restricted
(tools=[] or a single 'no_more_tools' sentinel). Force the
model into text. Then store that as the final assistant message
instead of the canned fallback.

If that final-turn LLM call ALSO produces empty text (rare),
fall back to the canned message currently in place. So the
behavior is: real summary preferred, canned fallback as the
last resort.

## Spec

features/session/llm_interaction.feature (or similar) gets one
@wip scenario:

  Scenario: tool loop produces a non-empty final message when the LLM keeps requesting tools
    Given the following sessions exist:
      | name  |
      | loopy |
    And the following model responses are queued:
      | type     | tool_call | arguments | model |
      | toolCall | grep      | {}        | echo  |
      ... (11+ tool-only responses to exceed max-loops)
    When the user sends \"poke around\" on session \"loopy\"
    Then session \"loopy\" has transcript matching:
      | #index | type    | message.role | message.content |
      | -1     | message | assistant    | #\".+\"          |

The 11 queued tool-only responses guarantee max-loops is exceeded.
Negative-index assertion locks the last entry's content to
non-empty.

After the fix, an additional model response (text) needs to be
queued for the wrap-up turn — implementer chooses whether grover
synthesizes that or the test queues it explicitly.

## Definition of done

- Tool loop never stores an empty final assistant message (already
  partly done via canned fallback).
- The runtime calls the LLM once more with tools=[] (or equivalent)
  before falling back to the canned message.
- The canned message becomes a true last-resort, not the default
  on max-loops.
- bb features and bb spec green.
- Manual: in zanebot, queue a long tool sequence; observe Toad
  displays a real wrap-up message that references the work done.

## Distinct from related bugs

- isaac-utzs: compaction summaries lose agent identity. Different.
- isaac-lyau: orphan toolCalls without toolResults. Different —
  in tidy-comet's recent turn, toolCall/toolResult pairs are
  intact. This bug is about tool-loop exit without text.
- isaac-juhh: streaming for tool-using turns. Once that lands,
  partial content arrives during tool loops too — interaction
  between max-loops and streaming partial text needs care.

## Notes

Verification failed: automated acceptance is green (bb spec passes; bb features passes; targeted loop-exhaustion unit and feature coverage confirm the forced no-tools summary turn and canned fallback behavior). However, the bead definition of done still requires a manual zanebot/Toad smoke test demonstrating that a long real tool sequence produces a real wrap-up message visible in Toad. That manual evidence is not present here, so the bead cannot be closed yet.

