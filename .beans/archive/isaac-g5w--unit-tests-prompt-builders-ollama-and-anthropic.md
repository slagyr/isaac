---
# isaac-g5w
title: "Unit tests: prompt builders (Ollama and Anthropic)"
status: completed
type: task
priority: high
created_at: 2026-04-02T00:16:12Z
updated_at: 2026-04-04T00:45:37Z
---

## Description

spec/isaac/prompt/builder_spec.clj and spec/isaac/prompt/anthropic_spec.clj are missing.

Cover:
- builder: build with soul + transcript, post-compaction history, tool formatting, token estimation
- anthropic: system as separate field, cache breakpoints on system and penultimate user message, extra message fields stripped, tool formatting as input_schema

Use TDD skill. Follow speclj conventions.

