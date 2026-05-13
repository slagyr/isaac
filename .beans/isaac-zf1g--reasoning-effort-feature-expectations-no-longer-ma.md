---
# isaac-zf1g
title: reasoning effort feature expectations no longer match requests
status: scrapped
type: bug
priority: normal
created_at: 2026-05-12T04:42:26Z
updated_at: 2026-05-13T03:00:20Z
---

## Description

Why
Full bb features / bb ci are red outside isaac-yonq scope.

Observed behavior
Reasoning effort plumbing scenarios fail because expected request fields no longer match actual output:
- Provider-level reasoning-effort overrides the default
- Model-level reasoning-effort overrides provider-level
- Non-reasoning Chat-Completions model omits the field even when configured

Reproduction
Run bb features or bb ci.

Notes
This surfaced while re-verifying isaac-yonq after sync. The failure is separate from the manifest migration.

## Reasons for Scrapping

bb ci is fully green as of 2026-05-12. The reasoning effort scenarios pass — fixed as a side effect of subsequent merges.
