---
# isaac-uwso
title: "isaac.llm reorganization (umbrella)"
status: completed
type: feature
priority: normal
created_at: 2026-05-07T05:27:52Z
updated_at: 2026-05-07T16:24:17Z
---

## Description

Reorganize the llm/provider/api layer to make the three concepts crisp and cleanly separated:

- api  — wire-spec id (the registry key); one impl ns per api
- provider — config bundle: {:api :auth :base-url :api-key :models}; pure data
- model — string passed through to the api

Target structure (all under isaac.llm to keep the LLM concerns together; later promotable to a package):

- isaac.llm.api  — Api protocol + registry (replaces today's isaac.provider)
- isaac.llm.api.<id>  — one ns per wire spec
   - isaac.llm.api.anthropic-messages
   - isaac.llm.api.openai-completions
   - isaac.llm.api.openai-responses
   - isaac.llm.api.ollama
   - isaac.llm.api.claude-sdk
   - isaac.llm.api.grover
- isaac.llm.providers  — catalog of known providers with default :api / :base-url / :auth / :models; later promotable to isaac.llm.providers.<name>

Sub-beads carry the actual work; this umbrella tracks the layering and exists to coordinate sequencing.

## Acceptance Criteria

All three sub-beads closed; bb spec and bb features green; the term 'LLM' no longer appears as a synonym for the wire/api impl in code.

## Notes

Verification failed: bb spec and bb features are green, and the sub-beads are now closed, but the code still uses 'LLM' as a synonym for the wire/api impl. Examples: src/isaac/llm/api.clj:2 describes the protocol as an 'LLM API adapter', and src/isaac/llm/api/grover.clj:2 describes Grover as a 'test LLM provider'.

