---
# isaac-cgm1
title: "Compaction triggers on cumulative cost instead of conversation size"
status: completed
type: bug
priority: high
created_at: 2026-05-01T19:37:04Z
updated_at: 2026-05-03T16:46:42Z
---

## Description

Marvin (and any active session) compacts far more often than expected.
Investigation traced this to the compaction trigger comparing the
cumulative billed token count against the context window — the value
grows monotonically across turns regardless of how many messages were
dropped/summarized, so eventually every session hits 90% and compacts.

Root causes (all in the live conversation path):

1. `should-compact?` (isaac.session.compaction:40-43) compares session
   `:total-tokens` against the threshold. `:total-tokens` is incremented
   by `update-tokens!` in storage.clj on every turn (input+output). It
   represents cumulative cost, not the prompt size the model just saw.

2. The per-turn compaction signal we want — `usage.input_tokens` from
   the most recent response — is observed in `extract-tokens`
   (drive/turn.clj:48-54) but never persisted as a session-level field
   for the trigger to read.

3. Single huge tool results amplify (1) because compaction's
   `tool-pair-message` (context/manager.clj:88-96) inlines the FULL
   tool result via "I called tool X with args Y. The tool result was:
   <FULL>", bypassing the head-and-tail truncation that the live prompt
   path (prompt/builder.clj:7-19) applies. The summarizer then sees a
   much larger prompt than the conversation it is summarizing.

4. `store-response!` (drive/turn.clj:147-161) drops per-entry
   `:tokens`, so transcript entries lack the field needed for accurate
   per-message accounting / diagnostics.

Spec: features/session/context_management.feature

Renames committed (existing scenarios switched to last-input-tokens):
- `Compaction triggers at 90% context usage`
- `Conversation is compacted into a summary`

New @wip scenarios:
- `Cumulative billing across many small turns does not trigger compaction`
- `Compaction summarizer receives truncated tool results`
- `last-input-tokens is updated from response usage on every turn`
- `Assistant response persists per-entry token count`

Implementation notes:
- Add `:last-input-tokens` to session state; populate from
  `usage.input_tokens` on every response in `update-tokens!` (replace,
  not add). Keep `:total-tokens` accumulating as cost only.
- Make `should-compact?` read `:last-input-tokens`.
- Pass `:tokens` through `store-response!` so it lands on assistant
  entries.
- Reuse `truncate-tool-result` inside `tool-pair-message` (or in the
  compactables pipeline) so the summarizer prompt is bounded.

Definition of done:
- All four @wip scenarios pass; @wip removed.
- Existing renamed scenarios still pass.
- bb test green.

## Notes

Verified current checkout: context compaction now uses last-input-tokens, persists assistant entry tokens, truncates tool results in compaction requests, all new context_management scenarios are active and green. bb spec and bb features both pass.

