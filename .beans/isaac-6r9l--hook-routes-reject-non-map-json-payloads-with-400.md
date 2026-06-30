---
# isaac-6r9l
title: Hook routes reject non-map JSON payloads with 400
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-30T14:49:00Z
updated_at: 2026-06-30T14:50:49Z
---

## Problem

On zanebot, repeated `POST /hooks/workout` requests are returning `500 Internal
Server Error`.

The live service log shows the actual exception:

- `:server/request-failed`
- `:uri "/hooks/workout"`
- `:ex-class "IllegalArgumentException"`
- `:error-message "contains? not supported on type: clojure.lang.LazySeq"`

This is happening before hook dispatch. Other health hooks continue to dispatch
normally.

## Root cause

`isaac-hooks` parses the JSON request body and passes it directly into hook
template rendering. The template renderer assumes the payload vars are a map and
calls `contains?` / `get` on them.

When the incoming JSON body is a top-level sequence rather than a map, that
assumption explodes and the HTTP wrapper turns it into a generic `500`.

That is the wrong contract for hook ingress. A malformed or unsupported payload
shape should fail as a client error, not an internal server error.

## Desired behavior

- Hook routes must validate that a successfully parsed JSON body is a map before
  attempting template substitution or dispatch.
- If the body is valid JSON but not a map, the route must return:
  - HTTP `400`
  - plain-text `Bad Request`
- The request must not create or dispatch any hook turn in that case.
- Existing map-shaped hook payloads must remain green.

## Likely repo scope

- `isaac-hooks`
- possibly a small assertion in `isaac-server` only if the current HTTP wrapper
  surface needs an updated expectation

## Acceptance

- A hook POST with invalid JSON still returns `400 Bad Request`.
- A hook POST with valid JSON that is a top-level array returns `400 Bad Request`.
- A hook POST with valid JSON that is a scalar or other non-map JSON value
  returns `400 Bad Request`.
- A normal map-shaped hook payload still returns `202 Accepted` and dispatches.

## Notes

Observed concretely on `zanebot` for the `workout` hook, but the fix should be
generic for all hook routes rather than special-cased to one hook name.
