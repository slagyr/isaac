---
# isaac-nfmt
title: "Switch Isaac config keys from camelCase to kebab-case"
status: completed
type: task
priority: normal
created_at: 2026-04-18T15:11:05Z
updated_at: 2026-04-19T23:13:54Z
---

## Description

Isaac's config uses camelCase keys (:contextWindow, :baseUrl, :apiKey, :authKey, :assistantBaseUrl, :responseFormat, :streamSupportsToolCalls, :supportsSystemRole) — atypical for Clojure. Rename to kebab-case throughout.

## In scope
- Config schema: src/isaac/config/schema.clj, src/isaac/config/loader.clj
- Config consumers in src/ (crew, chat, context manager, etc.)
- Feature file EDN content: features/**/*.feature (composition, cli/config, tools/filesystem_boundaries, and any other places that construct config)
- Unit specs that construct config data: spec/**/*_spec.clj
- Built-in default config (bundled)

## Out of scope
- External API request/response bodies (Anthropic JSON, OpenAI JSON, etc.). Those are wire formats dictated by the provider — they must stay as-is. Grep will hit them (e.g. src/isaac/llm/anthropic.clj uses :apiKey when POSTing); leave those alone.
- Internal EDN keys that happen to match external formats and are converted at the boundary — leave them as-is too.

## Mapping (Isaac config only)
:contextWindow          → :context-window
:baseUrl                → :base-url
:apiKey                 → :api-key
:authKey                → :auth-key
:assistantBaseUrl       → :assistant-base-url
:responseFormat         → :response-format
:streamSupportsToolCalls → :stream-supports-tool-calls
:supportsSystemRole     → :supports-system-role

(Verify by grep — any others discovered get the same treatment.)

## Verification
- bb features passes (no regression)
- bb spec passes
- isaac config print output shows kebab-case
- isaac config validate rejects camelCase versions of these keys (warning: unknown key), or — if we want migration grace — accept both for one release with a deprecation warning. Decide as part of this bead.

## Background
Observed 2026-04-18. ~157 raw grep hits across 41 files; final rename count is smaller because external-API keys are excluded.

## Acceptance Criteria

1. All Isaac-config key references in src/ use kebab-case
2. All feature files use kebab-case for Isaac config EDN
3. All unit specs that construct Isaac config use kebab-case
4. External API wire formats (anthropic.clj, openai_compat.clj, grover.clj request bodies) are unchanged
5. bb features passes
6. bb spec passes
7. isaac config (pretty-printed) output shows kebab-case keys

## Notes

Decision (2026-04-18): hard cutover. No migration grace period. camelCase versions of these keys become unknown-key warnings (or errors for required fields) immediately. Isaac is early-stage; few real configs in the wild; migration code is unjustified complexity.

