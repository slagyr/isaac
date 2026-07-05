---
# isaac-0h7b
title: Compaction/offset can orphan a tool-result; codex Responses API rejects it
status: draft
type: bug
priority: high
created_at: 2026-07-05T16:46:48Z
updated_at: 2026-07-05T16:46:48Z
---

## Problem

Compaction (and the effective-history-offset boundary) can split a tool-call / tool-result pair, leaving an ORPHANED `function_call_output` (a tool result whose matching tool call is not in the effective head). The chatgpt/codex Responses API rejects the request: `invalid_request_error: No tool call found for function call output with call_id <id>`. The anthropic/messages API tolerates it — so the session works on Opus but fails on codex.

## Evidence (2026-07-05, zanebot, isaac-work-1)

- After fixing work-1's mid-line offset (isaac-63f3), work-1 still failed on codex: `No tool call found for function call output with call_id f8b602ea-...`.
- Same session succeeded on `--with-model opus` (messages API tolerates the dangling result).
- Root: the effective-head boundary (a compaction entry) falls AFTER a tool result but its originating tool call was compacted away / sits before the boundary.

## Desired behavior (any/all)

- Compaction must never cut between a tool_call and its tool_result — keep the pair together (extend the head boundary to include the call, or exclude the trailing result).
- AND/OR the prompt renderer must DROP an orphaned tool result (a `function_call_output` with no matching call in the rendered messages) before sending — so the request is valid for every provider (responses API included).
- The effective head, once built, must be a self-consistent tool-call/result graph regardless of provider.

## Scope

isaac-agent: compaction splice (session/compaction or drive/turn), `llm/prompt/builder` message rendering, and the responses provider path (`llm/api/responses`). Relates to isaac-63f3 (offset) — both are effective-head integrity.

## Acceptance (gherkin, isaac-agent)

- Given a session whose effective head begins after a tool_result whose tool_call is not in the head, when the prompt is built for the responses API, then the orphaned result is dropped (or the boundary is adjusted) and the request is accepted (no "No tool call found" error).
- Given a normal paired tool_call/result in the head, both are preserved.

Priority: HIGH — silently makes a session unusable on codex; only surfaces as a provider-specific rejection.
