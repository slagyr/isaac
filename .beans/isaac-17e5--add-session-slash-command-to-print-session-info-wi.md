---
# isaac-17e5
title: "Add /session slash command to print session info without hitting LLM"
status: draft
type: feature
priority: deferred
tags:
    - "deferred"
created_at: 2026-04-29T23:34:29Z
updated_at: 2026-04-29T23:34:38Z
---

## Description

Users need a way to inspect current session state (id, tokens used, context window, crew, channel, etc.) without triggering an LLM call. Add a /session slash command that prints this information directly from local state.

