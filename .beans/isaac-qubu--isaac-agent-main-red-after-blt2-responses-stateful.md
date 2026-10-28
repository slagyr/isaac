---
# isaac-qubu
title: 'isaac-agent main red after blt2: responses stateful scenarios'
status: in-progress
type: task
priority: high
tags:
    - agent
created_at: 2026-10-28T08:53:09Z
updated_at: 2026-10-28T14:50:12Z
---

## Why

isaac-agent main went red with isaac-blt2 (61329e1): CI `bb ci` → `features/llm/api/responses/stateful.feature:127` and `:154` fail — `body.store`, `body.input…`, `body.previous_response_id` come back nil. 60d8186, just before it, was green.

blt2 changed `spec/isaac/agent/llm/providers_steps.clj` `request-for-match` to `update :body`, and `record-outbound-request!` now records `:body nil` for callers that pass none. The last-outbound-request matcher now likely picks a recorded entry with a nil body over grover's provider request. Production code is unaffected (only the scope string and the recorder's arity changed) — this is a harness regression, but main must be green.

## What

Make both stateful scenarios pass again without weakening them, keeping blt2's login-body assertion working.

## Acceptance

- `bb features` and `bb jvm-features` green on isaac-agent main; CI green.
