---
# isaac-qubu
title: 'isaac-agent main red after blt2: responses stateful scenarios'
status: completed
type: task
priority: high
tags:
    - agent
created_at: 2026-10-28T08:53:09Z
updated_at: 2026-10-28T15:20:19Z
---

## Why

isaac-agent main went red with isaac-blt2 (61329e1): CI `bb ci` → `features/llm/api/responses/stateful.feature:127` and `:154` fail — `body.store`, `body.input…`, `body.previous_response_id` come back nil. 60d8186, just before it, was green.

blt2 changed `spec/isaac/agent/llm/providers_steps.clj` `request-for-match` to `update :body`, and `record-outbound-request!` now records `:body nil` for callers that pass none. The last-outbound-request matcher now likely picks a recorded entry with a nil body over grover's provider request. Production code is unaffected (only the scope string and the recorder's arity changed) — this is a harness regression, but main must be green.

## What

Make both stateful scenarios pass again without weakening them, keeping blt2's login-body assertion working.

## Acceptance

- `bb features` and `bb jvm-features` green on isaac-agent main; CI green.


## Implementation (scrapper@bean-isaac-qubu)

branch: bean/isaac-qubu @ 7787091 (base origin/main@f74547c). Prefer Grover provider requests in simulated turns over a concurrent OAuth HTTP capture; added regression for provider request matching while OAuth capture exists. Focused scenario and regression, full bb features (947/0), bb jvm-features (947/0; ISAAC_TEST_TIMEOUT_MS=600000), bb ci (spec + features) green. Gate verify exited 2: no feature-baseline. Landing/CI on main pending verifier.



## Landed on main (2026-10-28)

main-sha: isaac-agent 91bffe0ed17bbfe40c93bb411b584fadd39d095e

Verified by perceptor@isaac-verify-2: bb ci (1982 specs/0 failures; 947 features/0 failures, 1 pre-existing pending), ISAAC_TEST_TIMEOUT_MS=600000 bb jvm-features (947/0, 1 pre-existing pending). Stateful assertions unchanged; Grok OAuth scope assertion remains in the green suite.
