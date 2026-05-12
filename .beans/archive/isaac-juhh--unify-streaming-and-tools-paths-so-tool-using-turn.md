---
# isaac-juhh
title: "Unify streaming and tools paths so tool-using turns stream content"
status: completed
type: feature
priority: normal
created_at: 2026-04-29T16:07:09Z
updated_at: 2026-04-29T21:37:45Z
---

## Description

The drive currently has two paths through stream-result
(src/isaac/drive/turn.clj:213):

  (if (:tools request)
    dispatch-chat-with-tools             ;; non-streaming, full response
    (if cli-channel?
      print-streaming-response            ;; streams (CLI no-tools only)
      dispatch-chat))                     ;; non-streaming fallback

Result: any turn with tools advertised — i.e. all crew sessions —
gets the entire LLM response delivered as one on-text-chunk call.
ACP/Toad sees the agent stall for the full turn duration, then
receive a wall of text. Observable on zanebot's clever-signal
session.

The streaming infrastructure already exists for both paths; the SSE
delta parsing in openai_compat/process-responses-sse-event already
distinguishes text deltas from tool_call deltas. The forwarding of
text deltas to on-text-chunk during a tool-using turn is the gap.

## Unified streaming-with-tools loop

Replace the two-path branch with a single streaming loop:

  loop:
    open SSE
    on text delta            -> on-text-chunk
    on tool_call delta       -> accumulate
    on finish_reason "tool_calls" -> execute accumulated, recurse
    on finish_reason "stop"  -> done

When no tools fire, the tool_call accumulator stays empty and the
loop returns after the first finish_reason. Same path covers
no-tools and tools-using cases. :tools on the request becomes a
payload toggle, not a routing key.

## Provider scope

- openai-compatible (chat/completions and /responses): both already
  expose SSE; just route through it.
- anthropic-messages: also SSE; chat-stream exists.
- ollama: SSE-ish; chat-stream exists.
- claude-sdk: not currently in use; leave its narrow path.
- grover: test stub. May need an SSE simulation for the spec
  below — likely a new :type "text-stream" queued response that
  emits each vector element as a separate delta.

## Spec

features/session/llm_interaction.feature (or similar — pick the
fitting file) gets one @wip scenario:

  Scenario: tools-using turns stream text deltas as they arrive
    Given the crew "main" allows tools: grep
    And the following model responses are queued:
      | type        | content                  | model |
      | text-stream | ["Hel","lo, ","there"]   | echo  |
    When the user sends "hi" on session "stream-test" via memory channel
    Then the memory channel has events matching:
      | event      | text  |
      | text-chunk | Hel   |
      | text-chunk | lo,   |
      | text-chunk | there |

Memory-channel events are the right surface — assert on the engine
output, not the comm rendering.

## Implementation surface

- src/isaac/drive/turn.clj stream-result — collapse to one path
- src/isaac/drive/turn.clj stream-and-handle-tools! — adjust to
  drive the unified loop
- src/isaac/llm/openai_compat.clj chat-stream — extend to handle
  tools (or wire chat-with-tools to use chat-stream internally)
- src/isaac/llm/grover.clj — accept :type "text-stream" with vector
  content; emit fragments as SSE deltas

## Definition of done

- features scenario above passes
- Manual on zanebot/Toad: long Marvin response visibly streams in
  Toad's UI, not as a single block
- bb features and bb spec green
- ACP existing scenarios still pass (the agent_message_chunk
  notification path)

## Out of scope

- Anthropic streaming with tools (separate concern; their tool-use
  protocol is shaped differently)
- claude-sdk streaming (not in use)

## Notes

Manual verified by Micah: long Marvin responses visibly stream in Toad UI word-by-word rather than arriving as a single block. All automated DoD items (bb spec, bb features, ACP streaming spec) also green.

