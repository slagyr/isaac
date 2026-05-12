---
# isaac-79r
title: "Instrument server requests with structured logs"
status: completed
type: task
priority: high
created_at: 2026-04-08T16:52:54Z
updated_at: 2026-04-08T18:09:57Z
---

## Description

Add structured EDN logging to the HTTP server boundary.

## Scope
- Log request lifecycle events and request failures as structured maps
- Include route, method, status, exception type, and error message where appropriate
- Use structured maps only
- Add or update specs/features for request logging where appropriate

## Reviewed feature expectations
- Acceptance tests should be able to configure logging with:
  Given config:
    | key        | value  |
    | log.output | memory |
- Request steps should support future request shaping, e.g.:
  When a GET request is made to "/error":
    | request field  | value |
    | params.message | Boom  |
- Successful request lifecycle should be logged at :debug with entries like:
  :server/request-received and :server/response-sent
- Failed requests should log :server/request-failed with route, method, status, ex-class, and error-message
- Use a real /error route for deterministic server failure tests instead of redef-based seams

## Worker note
- This feature direction has been reviewed and approved
- Use the config table pattern and rotated log matcher table in the feature/spec work
- Do not re-close this bead as duplicate/complete without implementing the approved feature updates and corresponding tests

## Notes
- This bead depends on the initial server skeleton work
- Include file/line capture via the logging macros
- Prefer curated request/response context over dumping raw full maps

