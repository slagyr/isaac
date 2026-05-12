---
# isaac-7t1
title: "Post-turn tool summarization: abbreviate tool results after turn completes"
status: draft
type: feature
priority: normal
tags:
    - "deferred"
created_at: 2026-04-15T13:52:24Z
updated_at: 2026-04-17T04:28:57Z
---

## Description

After a tool-heavy turn completes, the raw tool call results (file contents, command output) remain in the transcript at full size. The LLM already processed this data and produced its response — the raw results are redundant.

After the turn's response is stored, replace raw tool call/result entries with abbreviated summaries: what tool was called, what it found, condensed to one line each. The LLM's own response is the proof it reasoned correctly.

This keeps the transcript lean for subsequent prompts without affecting LLM reasoning.

## Acceptance Criteria

After a tool-heavy turn, raw tool results in the transcript are replaced with abbreviated summaries. Subsequent prompts use less tokens. LLM reasoning is unaffected.

