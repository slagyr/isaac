---
# isaac-5l31
title: Use :response-id to chain model requests
status: draft
type: feature
priority: normal
created_at: 2026-09-15T22:03:49Z
updated_at: 2026-09-15T22:03:49Z
---

## Intent (2026-09-15, Micah)

The provider response schema keeps `:response-id` (see isaac-g71i). Micah: "we should actually start using it" — make real use of provider response ids to chain requests instead of re-sending context the provider already holds.

## Current state (agent main cd90423)

- Only the Responses API (chatgpt/openai) returns a usable id. The generic tool loop threads `:previous_response_id` itself (tool_loop.clj:38-53, 128-152) when the provider config is `:stateful`, and falls back to full context when the id is not found (regex over the error message, tool_loop.clj:40-47).
- Output-token stamping already distinguishes stateful providers (`replayable-output-tokens`).

## Questions to settle (DEFERRED — not decided)

- Scope: within a turn only (tool cycles), or across turns in a session (persist the last response id on the session)?
- Where chaining lives once the schema lands: the tool loop (generic, keyed on `:response-id`) or the adapter.
- Compaction interaction: a compaction or transcript edit invalidates the provider-side chain.
- Token accounting when the provider holds context: `:prompt-tokens` should still report the full context processed.
- Which providers besides the Responses API offer server-side conversation state worth using.
