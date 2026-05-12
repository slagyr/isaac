---
# isaac-ban
title: "Unit test: Anthropic prompt builder strips extra message fields"
status: completed
type: task
priority: normal
created_at: 2026-04-02T00:10:31Z
updated_at: 2026-04-07T23:40:48Z
---

## Description

Transcript messages contain metadata (model, provider, timestamp, etc.) that Anthropic's API rejects as extra inputs.

## Test
In spec/isaac/prompt/anthropic_spec.clj:
- Given a transcript with messages containing extra fields (model, provider, etc.)
- When a prompt is built
- Then messages only contain role and content

## Bug Reference
Anthropic returned 400: "messages.1.model: Extra inputs are not permitted"
Fixed by adding select-keys in prompt/anthropic.clj extract-messages.

