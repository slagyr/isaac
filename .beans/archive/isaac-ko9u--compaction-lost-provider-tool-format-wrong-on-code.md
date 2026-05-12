---
# isaac-ko9u
title: "Compaction lost :provider; tool format wrong on Codex; specs missed it"
status: completed
type: bug
priority: high
created_at: 2026-04-28T23:43:46Z
updated_at: 2026-04-29T00:24:07Z
---

## Description

Live failure on zanebot's clever-signal: every compaction returned
HTTP 400 from openai-codex with "Missing required parameter:
'tools[0].name'". After 6 consecutive failures the session was
disabled with :compaction-disabled true.

## Root cause (already fixed in main)

src/isaac/drive/turn.clj `perform-compaction!` was calling
ctx/compact! without :provider in its options map. provider was nil
inside compact!, which propagated to compaction-request →
build-tools-for-request. With nil, codex-provider? returns false, so
the chat-completions nested format was emitted:

  {:type "function" :function {:name "memory_write" ...}}

instead of the Codex responses flat format:

  {:type "function" :name "memory_write" ...}

The fix added :provider to the options map.

## Spec gap (this bead)

The existing compaction scenarios all use grover as the provider, and
grover doesn't enforce Codex tool-format quirks. So compaction-with-
nil-provider passed every check. The bug was only caught in production.

## Spec — already drafted

features/context/compaction.feature has a new @wip scenario:
"compaction passes the session's provider through so tool format
matches". It sets up an openai-codex-shaped provider, drives a
compaction-triggering turn, and asserts the outbound HTTP request
emits Codex-flat tool format (tools.0.name at top level, NO
tools.0.function nesting).

No new steps invented — all phrases already exist:
- the isaac EDN file ... exists with:
- the following sessions exist:
- session ... has transcript:
- the following model responses are queued:
- the user sends ... on session ...
- the last outbound HTTP request matches:
- the last provider request does not contain path "X"

## Also wanted: unit spec

A focused unit spec on perform-compaction! asserting the options map
handed to ctx/compact! includes :provider. Three lines, no provider
config needed. Catches the same bug at the call site rather than at
the wire format.

## Related fixes from the same session

- src/isaac/cli/prompt.clj CollectorChannel was missing on-thought-chunk
  → tracked separately in isaac-x34t.
- src/isaac/llm/http.clj didn't log :response-body on errors, hiding
  the actual API rejection message (already shipped).

## Definition of done

- features/context/compaction.feature scenario passes without @wip
- Unit spec on perform-compaction! asserts :provider is threaded
- Verified the scenario fails BEFORE the fix is reverted (sanity-
  check the test actually catches the regression)
- bb features and bb spec green

## Notes

Removed stale @wip from the provider-threading compaction scenario, updated the scenario to inspect the first outbound Codex compaction request, and added a focused unit spec that check-compaction!/perform-compaction! threads :provider into ctx/compact!. Added feature-test request history capture so multi-request turns can assert against the compaction request reliably. Sanity-check performed by temporarily removing :provider from src/isaac/drive/turn.clj: the new unit spec and targeted feature scenario both failed, then the fix was restored. Full bb spec and bb features are green.

