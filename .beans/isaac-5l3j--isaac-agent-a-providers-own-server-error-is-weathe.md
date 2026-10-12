---
# isaac-5l3j
title: 'isaac-agent: a provider''s own server error is weather, decided by error code'
status: in-progress
type: bug
priority: high
created_at: 2026-10-12T21:29:44Z
updated_at: 2026-10-12T21:31:42Z
---

Scenarios approved by Micah 2026-10-12 ("I think we should try"). Follows isaac-i2i4 (overloaded providers and dropped connections are weather).

## What happened

2026-10-12 on zanebot a worker turn on chatgpt failed mid-stream with "An error occurred while processing your request. You can retry your request…". Isaac classified it as a plain `:api-error`. The crew had two fallback models; neither was tried. The turn ended with outcome `error`, and the bean it had claimed sat `in-progress` until a person re-hailed it.

`:api-error` is deliberately outside `provider-wall/fallback-reasons`, on the theory that it means our request was bad. isaac-i2i4 rescued only failures whose message says "overloaded".

## Rule

Decide by codes, never by the wording of the message (Micah, 2026-10-12: "I hate depending on model text").

A failure is `:server-error`, a new weather reason, when it is:

- a Responses `response.failed` or `error` stream event whose error `code` is `server_error`;
- such an event that carries no code at all (the provider blamed nothing on the request);
- HTTP 500.

`:server-error` behaves like `:overloaded`: the turn continues on the crew's next model; with no model left it suspends and resumes on the usual backoff.

Anything the provider blames on the request still ends the turn: any other failure code (`invalid_prompt`, …), any other 4xx. Existing classifications keep priority: 401/403 auth, 429, 502/504/529, context overflow, the provider wall.

## Work

1. `isaac.agent.llm.api.responses`: keep the failure's `code` on the stream error (from `response.error.code` / `error.code`, and from a top-level `error` event if one arrives).
2. `isaac.agent.llm.api.protocol/normalize-error`: classify `:server-error` per the rule; add it to the error enum.
3. `isaac.agent.drive.provider-wall` (`stall-response?`, `fallback-reasons`) and `isaac.agent.drive.weather` (`weather-reason`): treat `:server-error` as weather.
4. Log the failure's `:code` and `:status` on `:chat/response-failed`, so the next odd failure shows what the provider sent.
5. Grover fixture: a queued `stream-failed` response takes a `code` column and puts it on the failure event's error.
6. Remove the three `@wip` tags in `features/llm/provider_server_error.feature`; unit specs for the classifier.
7. Handbook: add `:server-error` wherever the weather reasons are listed.

## Not in this bean

- isaac-i2i4's "overloaded" check still matches message text. Moving it to the `server_is_overloaded` code is a follow-up; do not edit its frozen scenarios here.
- No give-up budget for suspended turns. A code-less failure that is really a bad request retries on backoff (capped at 30 minutes) and raises attention. Accepted for now; the new log field will show whether it happens.

## Acceptance

- `features/llm/provider_server_error.feature`: all scenarios pass with no `@wip` (four fail today; the two "still ends the turn" examples already pass and must keep passing).
- `features/llm/provider_overload.feature` and `model_fallback.feature` unchanged and green.
- `bb ci` and `bb jvm-spec` green; CI green on main.

feature-baseline: isaac-agent d75ea473acb9d179d6a99b8b50e6b328eb8e7d1e
feature-blob: isaac-agent features/llm/provider_server_error.feature ba1c34f39ab23487e29b2d0b79bbd7775e999b83
