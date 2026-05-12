---
# isaac-ga9o
title: "Capture and persist reasoning.summary on Responses API turns"
status: completed
type: feature
priority: low
created_at: 2026-05-01T18:24:19Z
updated_at: 2026-05-05T23:41:46Z
---

## Description

The OpenAI Responses API accepts `reasoning.summary: "auto" |
"concise" | "detailed"` on the request, and returns a human-readable
summary of the model's hidden reasoning trace under
`response.reasoning.summary`.

Codex CLI sends `summary: "auto"` by default — that is what produces
the nice 'Considered X, then Y' annotations in their UI. Isaac
currently sends nothing.

## Scope

- Send `body.reasoning.summary: "auto"` on Responses API requests
  (only when reasoning effort is non-none)
- Capture `response.reasoning.summary` in the diagnostic log added
  by isaac-ibme
- Surface the summary somewhere visible (Discord reply? ACP
  thought-stream channel? TBD by consumer)

## Why deferred

isaac-ibme intentionally scoped to the effort knob to avoid bloat.
Summary capture is parity-with-Codex-CLI, useful for debugging 'why
did it answer this way' but not blocking the dumb-Marvin fix.

## Depends on

- isaac-ibme (defines the request/response handling shape)

## Notes

Finished ga9o on top of current main. Added Responses API request coverage for summary=auto and effort=none omission, added diagnostic log coverage for reasoning summary, taught the Grover Responses simulator to carry reasoning metadata for acceptance tests, and fixed transcript persistence to read reasoning/usage from the nested raw OpenAI response payload. Verified with bb spec (1270 examples, 0 failures) and bb features (494 examples, 0 failures).

