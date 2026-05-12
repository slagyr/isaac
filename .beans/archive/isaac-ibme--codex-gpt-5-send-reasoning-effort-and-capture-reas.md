---
# isaac-ibme
title: "Codex/gpt-5: send reasoning-effort and capture reasoning info on responses"
status: completed
type: feature
priority: high
created_at: 2026-05-01T17:00:51Z
updated_at: 2026-05-05T23:27:37Z
---

## Description

## Background

Marvin (and any other crew using `oauth-device` codex/gpt-5) feels
noticeably dumber through Isaac than through the Codex CLI hitting the
same model. Diagnostic logging confirmed why:

```
:reasoning  {:effort \"none\", :summary nil}
:usage      {:input_tokens 3155, :output_tokens 246,
             :output_tokens_details {:reasoning_tokens 0}}
:model      \"gpt-5.4\"
```

Isaac sends no `reasoning` field on Responses API requests. The API's
default is `effort: \"none\"` — reasoning tokens are 0. Codex CLI
defaults to `\"high\"`. Same model, two very different products.

## Scope

### 1. Send `reasoning.effort` on the request

The Responses API accepts a `reasoning` block:
```json
{ \"reasoning\": { \"effort\": \"high\", \"summary\": \"auto\" } }
```

Plumb a `:reasoning-effort` knob through provider/crew config so it
flows into the request body in
`isaac.llm.openai-compat/->codex-responses-request`.

Resolution chain (mirrors the crew/model resolution we use elsewhere):
  1. crew-level: `:crew.<id>.reasoning-effort`
  2. model-level: `:models.<id>.reasoning-effort`
  3. provider-level: `:providers.<provider>.reasoning-effort`
  4. fallback: `\"high\"` for codex-family (`openai-codex`, `gpt-5*`),
     `\"medium\"` for everything else on the Responses API path

Valid values: `\"none\"`, `\"low\"`, `\"medium\"`, `\"high\"` (per
OpenAI docs). `\"auto\"` for `summary` is fine as a default since it
gives us free reasoning summaries when present.

Chat Completions path (non-codex): the field doesn't apply, but
gpt-5/o-series via Chat Completions accept `reasoning_effort` at the
top level — pass it there too if the crew/model is a reasoning model.

### 2. Capture reasoning + usage details on the response

The diagnostic log line we added during investigation was very useful:
```clojure
(log/info :openai-compat/responses-usage
          :model     (:model result)
          :usage     (:usage result)
          :reasoning (get-in result [:response :reasoning]))
```

Make this permanent (probably at `:debug` level for routine traffic,
`:info` if reasoning is unexpectedly degraded). It surfaces:
  - effort actually applied (in case the API silently caps requests)
  - reasoning_tokens spent (validates the knob is wired)
  - reasoning summary (debugging context)
  - cached_tokens (cost visibility — already in input_tokens_details)

### 3. Persist usage/reasoning on assistant transcript entries

Right now Isaac's storage path strips the response down to
`{:role :content :model :provider :crew}` — `parse-usage` reduces the
rich usage block to `{:input-tokens, :output-tokens}` and reasoning is
discarded entirely. Without this, we can't audit historical turns or
build any cost dashboards.

Extend the assistant message stored in the transcript to include:
  - `:usage` with full token detail (`input_tokens`, `output_tokens`,
    `reasoning_tokens`, `cached_tokens`)
  - `:reasoning` with `{:effort ... :summary ...}` when present

(Storage already supports `:usage` and `:cost` on assistant messages
in some adapters; openclaw stores `usage/cost/stopReason/durationMs` —
see project memory `openclaw-session-format-comparison-2026-04-09`.)

## Acceptance

- A non-trivial Marvin turn produces `reasoning_tokens > 0` and a
  measurably better answer than today on a multi-step question.
- Provider/crew config can override effort: setting
  `:crew.marvin.reasoning-effort \"medium\"` produces a turn with
  effort=medium in the response.
- `bb spec spec/isaac/llm/openai_compat_spec.clj` (or wherever the
  unit tests live) covers the resolution chain and request body
  shape.
- Assistant transcript entries persist usage + reasoning fields.

## Out of scope

- Cost calculation/dashboards — capturing the data is the
  prerequisite; presenting it is separate.
- Auto-tuning effort based on prompt complexity. Static config is
  enough for now.

## Notes

- Investigation that confirmed the diagnosis was a temporary
  `log/info` in `chat-stream-with-responses-api` that printed
  `:reasoning` and `:usage` per turn. That code was reverted (per
  no-commit), but the structure is the right shape for the
  permanent capture path.
- Codex CLI sends `summary: \"auto\"` by default — worth matching for
  parity unless we have a reason to suppress summaries.

## Notes

Verification failed: the narrow OpenAI unit coverage passes, but the bead's written acceptance is still not fully met. The current behavior feature remains tagged @wip (features/llm/reasoning_effort.feature), and its current text only covers provider/model overrides, while this bead's acceptance still requires a crew-level override. The implementation in isaac.llm.openai-compat currently resolves reasoning effort from config-level :reasoning-effort plus model-family default, but I did not find end-to-end proof of the full crew -> model -> provider resolution chain from the bead description. Assistant transcript persistence of raw :usage and :reasoning is present in isaac.drive.turn/store-response!, but the bead remains incomplete at the acceptance-surface level. The bead is also still blocked by open follow-up issues (isaac-702d, isaac-ga9o, isaac-j2vx, isaac-qtui).

