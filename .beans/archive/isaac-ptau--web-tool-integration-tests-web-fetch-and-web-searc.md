---
# isaac-ptau
title: "Web tool integration tests: web_fetch and web_search against live services"
status: completed
type: task
priority: low
created_at: 2026-04-20T21:36:01Z
updated_at: 2026-04-21T16:17:41Z
---

## Description

Follow-up coverage for the web_fetch and web_search tools. Exercises real HTTP and the real Brave Search API to catch shape drift (API response changes, redirect handling, binary refusal in the wild). Tagged @slow so default bb features excludes them; run via bb features-slow. Requires BRAVE_API_KEY in env for web_search scenarios. See features/tools/web_fetch_integration.feature and web_search_integration.feature.

## Acceptance Criteria

1. Both base tools (isaac-j2fa web_fetch, isaac-nbb2 web_search) must be implemented first — this bead depends on them.
2. Add the 'the BRAVE_API_KEY environment variable is set' step-def.
3. Remove @slow wrap or leave as-is — scenarios run green via 'bb features-slow' whether or not we include them in a different task.
4. bb features-slow passes with BRAVE_API_KEY set.
5. bb features still passes (these scenarios remain excluded from it).

## Design

Integration tests live in separate feature files so stubbed behavior (in web_fetch.feature / web_search.feature) stays isolated from live-service variability. Minimal coverage only — one scenario per baseline behavior and one for redirect. Add a step-def 'the BRAVE_API_KEY environment variable is set' that pre-flight checks (System/getenv) and emits a pending with a clear message if missing, rather than failing.

## Notes

Implemented BRAVE_API_KEY preflight step in spec/isaac/features/steps/tools.clj. Verified targeted slow integrations pass: bb features-slow features/tools/web_fetch_integration.feature and bb features-slow features/tools/web_search_integration.feature:9. Full bb features-slow is still failing due unrelated existing slow scenarios: Anthropic API Key Authentication live test and Grok Authentication live test.

