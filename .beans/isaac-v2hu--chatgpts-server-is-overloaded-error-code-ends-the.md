---
# isaac-v2hu
title: chatgpt's server_is_overloaded error code ends the turn instead of falling back
status: completed
type: bug
priority: high
tags:
    - agent
    - llm
created_at: 2026-11-02T14:26:51Z
updated_at: 2026-11-02T14:37:32Z
---

Repo: **isaac-agent** (`src/isaac/agent/llm/api/protocol.clj`).

## Why

2026-11-02 on zanebot, two Foreman worker turns (isaac-p5d3, isaac-urog; crew scrapper, gpt-sol with fallbacks grok-4-6 and micah-opus) ended as errors after ~4 minutes and stalled their beans:

```
:chat/response-failed :code "server_is_overloaded" :error :api-error :provider "chatgpt"
  :message "Our servers are currently overloaded. Please try again later."
:turn/ended :ended-by :error :error :api-error
```

The 2026-10-07 fix (`features/llm/provider_overload.feature`) classifies an overload as `:overloaded` weather: fall back to the crew's next model, else suspend and resume. But the classifier (`protocol.clj` ~187) only reads the message when the error has **no code**:

```clojure
(and (nil? code) (str/includes? lower "overloaded")) :overloaded
```

chatgpt now sends `code "server_is_overloaded"`, so the check is skipped, the code isn't `"server_error"` either, and the error falls through as `:api-error`, which ends the turn with no fallback.

## Fix

An error whose code or message says overloaded is `:overloaded`, whatever the code. (Keep 429 → `:rate-limited` and the status rules ahead of it.)

## Acceptance

- `bb features features/llm/provider_overload.feature:66` — an overloaded `stream-failed` carrying code `server_is_overloaded` continues the turn on the crew's next model, logged `:turn/model-fallback` reason `:overloaded`. Run with `-t wip` until `@wip` is removed.
- The rest of `features/llm/provider_overload.feature` stays green (`bb features features/llm/provider_overload.feature`).
- Unit spec in the protocol classifier spec: a coded overload (`server_is_overloaded`) is `:overloaded`; `server_error` stays `:server-error`.

feature-baseline: isaac-agent e87dcd9b97ad5d2bd8e0f7a6d632caaa6533e899
feature-blob: isaac-agent features/llm/provider_overload.feature df634a6e51db450588ee2095f3811d0b6ad73995 66

## Implementation (2026-11-02)

`normalize-error` treats an overloaded code or message as `:overloaded` after status 429 and overloaded status handling. Protocol specs cover coded overload, code-only overload, message with another code, and existing `server_error`. Removed only the scenario's `@wip`. `bb features features/llm/provider_overload.feature`: 8 examples, 0 failures, 36 assertions. `bb ci`: 1990 specs / 958 features, 0 failures (one pre-existing pending). `bb bean-gate verify isaac-v2hu --dir isaac-agent=../isaac-agent-v2hu-land`: PASS on squash commit.

## Landed on main (2026-11-02)

main-sha: isaac-agent 5e2eff360288984d339aec81dc44e0a4ff288d06
