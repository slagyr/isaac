---
# isaac-92h
title: "Mid-turn compaction: check context during tool loops"
status: draft
type: feature
priority: normal
tags:
    - "deferred"
created_at: 2026-04-15T13:52:12Z
updated_at: 2026-04-17T04:29:19Z
---

## Description

Compaction only checks at the start of a turn. A single turn with many tool calls can exceed the context window before completing. The tool dispatch loop should check token count after each tool result and trigger compaction if approaching the limit.

This prevents the scenario where 20 file reads dump thousands of tokens and the next LLM call fails or gets rate limited.

## Acceptance Criteria

A turn with enough tool calls to exceed 90% of context window triggers compaction mid-turn. The turn completes successfully.

