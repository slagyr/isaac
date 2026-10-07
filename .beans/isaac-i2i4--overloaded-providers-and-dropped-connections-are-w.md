---
# isaac-i2i4
title: Overloaded providers and dropped connections are weather, not turn-ending errors
status: completed
type: bug
priority: high
created_at: 2026-10-07T17:22:58Z
updated_at: 2026-10-07T17:38:14Z
---

Micah, 2026-10-07. On zanebot, chatgpt answered two worker turns (isaac-kj14 twice) mid-stream with "Our servers are currently overloaded. Please try again later." and reset a third ("Connection reset", 15:43Z). The overload came back as `:api-error` (Responses `response.failed` with no status; `responses.clj` maps it to `:api-error`), the reset as `:unknown` (`llm/http.clj` catch-all), so each ended its turn with no fallback and no suspend. Three failures among hundreds of requests; OpenAI showed no Codex incident. Same class as isaac-l1b6 (dropped streams).

## Shape
- **Adapters report the fact.**
  - Responses: a `response.failed` / `error` event whose error code or message says overloaded → `{:error :overloaded}`.
  - Any API: HTTP 529, 502, 504 → `:overloaded`. 503 already walls; unchanged.
  - Transport (`llm/http.clj`): an IOException after connecting (SocketException "Connection reset", EOF, broken pipe, closed) → `:connection-lost`; connect failures keep `:connection-refused`.
- **The drive classifies it as weather.** `drive/provider_wall.clj` adds `:overloaded` and `:connection-lost` to `fallback-reasons`, classified like stalls: next model in the chain, else suspend with backoff.
- **Grover fixture:** queued `stream-failed` (emit the API's failure event with the given message; Responses: `response.failed`) and `connection-reset` (the transport's `:connection-lost` result).

## Acceptance (gated)
- The @wip scenarios in isaac-agent `features/llm/provider_overload.feature` (5 fallback examples + 2 suspend examples) pass with @wip removed.
- Unit spec: the transport maps a mid-request `java.net.SocketException: Connection reset` to `:connection-lost`, and a refused connect still to `:connection-refused`.
- Existing provider-wall, model-fallback, and stream_ended_early features stay green; a 400 contract error still does not fall back.
- `bb ci` and `bb jvm-spec` green.

Likely repo scope: isaac-agent. Deploy: zanebot and yopp (agent pin).

feature-baseline: isaac-agent ae72874307b611a50e0c1bb7e0d5dc5111f9ec1b
feature-blob: isaac-agent features/llm/provider_overload.feature da079b4a7ad286d803a96691b33994e4ff32f7a2

## Landed on main (2026-10-07)

main-sha: isaac-agent 67e2b5a49cb095d309d673ef89af3f044e9d5ae5
