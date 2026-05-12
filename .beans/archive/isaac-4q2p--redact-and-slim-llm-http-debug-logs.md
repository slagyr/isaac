---
# isaac-4q2p
title: "Redact and slim LLM HTTP debug logs"
status: completed
type: bug
priority: normal
created_at: 2026-04-29T15:29:42Z
updated_at: 2026-04-29T21:36:39Z
---

## Description

LLM HTTP debug logs currently emit oversized request/response metadata and can include sensitive Authorization bearer tokens in :headers. We need to keep request/response visibility while removing secrets and reducing noise to concise structured summaries. Acceptance: debug logs for :llm/http-request and :llm/http-response never include raw Authorization or other bearer tokens, request/response entries remain present but compact, and unit specs cover the redaction/summary behavior.

## Notes

Changed LLM HTTP logging to keep request/response/error entries compact and secret-free. Request/response/error logs now emit :header-keys plus body/response-body key+char summaries instead of raw header maps or raw response bodies, preventing Authorization bearer tokens from reaching logs. Added/redrove unit specs in spec/isaac/llm/http_spec.clj for JSON and SSE logging redaction/compactness. Verified with bb spec spec/isaac/llm/http_spec.clj. Full bb spec and bb features currently fail outside this bead's scope; tracked separately in a follow-up regression bead.

