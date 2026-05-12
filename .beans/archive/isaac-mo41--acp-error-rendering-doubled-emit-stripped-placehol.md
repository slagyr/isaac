---
# isaac-mo41
title: "ACP error rendering: doubled emit, stripped placeholder, no newlines between chunks"
status: completed
type: bug
priority: normal
created_at: 2026-04-29T18:12:07Z
updated_at: 2026-04-29T21:36:40Z
---

## Description

Toad showed the unknown-crew error as:

  use /crew  to switch, or add marvin to configunknown crew: marvin use /crew  to switch, or add marvin to config

Three distinct issues compound into that one display:

## 1. Doubled emission of error messages

src/isaac/drive/turn.clj reject-turn (lines 498-505):
  - Emits via comm/on-text-chunk
  - Returns a result with :error :unknown-crew

src/isaac/acp/server.clj run-acp-turn:
  - Sees :error on @turn-result and calls end-turn-with-error!
    which emits ANOTHER text update with the same message

Both reach Toad as agent_message_chunk, so the user sees the
message twice. Fix: pick one path for error rendering. Either
reject-turn doesn't emit (let run-acp-turn handle it) or
run-acp-turn skips end-turn-with-error! for results that already
emitted.

## 2. The <name> placeholder is stripped at render time

Source: "use /crew <name> to switch, or add <id> to config"

Toad renders this with the <name> literal removed (Toad's
markdown/html renderer strips angle-bracket tags). The user sees
"use /crew  to switch" with a double space.

Fix: use a placeholder format that survives rendering. Recommend
{name} — unambiguous, renderer-agnostic. Update the source string
in src/isaac/drive/turn.clj:500.

## 3. Multiple chunks concatenate without separator

Even if a single turn emits multiple distinct messages (error +
status, etc.), the ACP comm sends each as agent_message_chunk
which Toad concatenates inline. Distinct messages from the same
turn should be visually separable.

Fix: AcpChannel's on-text-chunk should append a newline when
the chunk contains complete-message-shaped text (or the engine
should always emit a trailing \n on its message blocks). Cheapest
implementation: ensure the source strings end with \n.

## Definition of done

- After triggering the unknown-crew flow on Toad: message appears
  exactly once, with the placeholder visible (e.g. "use /crew {name}
  to switch"), and any subsequent chunks render on their own line.
- Spec coverage in features/acp/error_response.feature or similar
  asserting the agent_message_chunk count for an unknown-crew turn
  is 1 (catches the doubling regression).

## Notes

Fixed ACP unknown-crew rendering so guidance is emitted exactly once, uses the renderer-safe placeholder {name}, and preserves embedded/trailing newlines through ACP chunk normalization. Added ACP error-response coverage plus ACP server unit coverage for the single-notification count, and updated existing unknown-crew expectations. Verified with targeted bb spec (spec/isaac/acp/server_spec.clj spec/isaac/cli/chat_spec.clj) and targeted bb features (features/acp/error_response.feature features/bridge/unknown_crew.feature). Full bb spec/features still have unrelated failures outside this bead's scope; those remain tracked separately.

