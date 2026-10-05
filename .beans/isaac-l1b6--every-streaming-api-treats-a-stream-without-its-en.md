---
# isaac-l1b6
title: Every streaming API treats a stream without its end marker as weather
status: in-progress
type: bug
priority: high
created_at: 2026-10-05T14:29:07Z
updated_at: 2026-10-05T14:32:57Z
---

Micah, 2026-10-05. On the night of 10-04, chatgpt closed seven streams empty 1–4 s after the request (zanebot: isaac-work-1/2/4 mid-turn, tempest vault-sync cron). The responses adapter reported each as a plain `:llm-error` ("responses stream ended without response.completed"), so the drive ended the turn: no fallback (scrapper has `:model-fallback [:grok-4-6 :micah-opus]`), no suspend, no retry; the hailed beans sat claimed with no worker.

Audit of the streaming adapters (isaac-agent `llm/api/`):
- responses: catches only an *empty* cut (`incomplete-responses-stream?`); a cut after some text passes as a response.
- messages: never checks `message_stop`; a missing `stop_reason` maps to `:other` and partial text returns as complete.
- chat_completions: never requires `finish_reason`; same `:other` pass-through.
- ollama: reads `done` but doesn't fail when it never arrives.

## Shape
- **Adapters report the fact.** Each streaming adapter returns `{:error :stream-ended-early}` when the stream closes before its end marker (`response.completed` / `message_stop` / `finish_reason` / `done true`), empty or partial. Nothing received is kept: no partial content, no tool calls.
- **The drive classifies it as weather.** `drive/provider_wall.clj` adds `:stream-ended-early` to `fallback-reasons` beside `:stream-stalled`, so the existing behavior applies: next model in the chain, else suspend with backoff (30 s growing, no provider Retry-After) and resume.
- **Grover fixture:** a queued `cut-off` response streams its content/tool call and stops before the API's end marker; complete responses now send each API's real end marker (messages currently omits `message_stop`).
- Out of scope: the claude-code CLI provider (not a streaming HTTP adapter).

Likely repo scope: isaac-agent.

## Acceptance (Micah signed off 2026-10-05; gated)
- The @wip scenarios in isaac-agent `features/llm/stream_ended_early.feature` (3 outlines x 4 APIs + 1 scenario) pass with @wip removed.
- Grover sends each API's real end marker on complete responses; existing API features stay green.
- `bb ci` and `bb jvm-spec` green.

feature-baseline: isaac-agent 4153a79c2c5ff9530b2aa85454a9388abf34917d
feature-blob: isaac-agent features/llm/stream_ended_early.feature 4eb473208480ce6a9940c15a5456a75a62ed4e55

## Worker checkpoint (2026-10-05)

Done: adapters reject missing end markers, Grover cut-off and end markers, provider-weather fallback; 13 acceptance examples pass, `bb ci` passed (1882 specs / 855 features). Focused native/JVM adapter specs pass (494 examples). Next: resolve `bb jvm-spec` red on unrelated `spec/isaac/agent/tool/comm_send_spec.clj:123` (expects default crew "main", gets "atticus" even when run alone); check whether current upstream main has same failure before touching out-of-scope files. Then rerun `bb ci`, `bb jvm-spec`, gate and land. Latest test run `bb jvm-spec spec/isaac/agent/tool/comm_send_spec.clj` red: 1 failure / 18 examples. Implementation changes remain uncommitted until full acceptance green.

## Acceptance conflict (2026-10-05)

Implementation pushed at isaac-agent `89dacd0` (`bean/isaac-l1b6`); `bb ci` green (1882 specs, 855 features), focused JVM LLM/provider-wall specs green (494). `bb bean-gate verify isaac-l1b6 --dir isaac-agent=../isaac-agent-isaac-l1b6` reports `PASS (isaac-agent @ HEAD 89dacd0 (branch bean/isaac-l1b6))`. Full `bb jvm-spec` remains red (1/1887 failures) on unrelated `spec/isaac/agent/tool/comm_send_spec.clj:123`: expected `{:crew "main" :session "dawn-watch"}`, got `{:crew "atticus" :session "dawn-watch"}`. Verified *the same failure* on clean isaac-agent `origin/main` at `4153a79` in a detached temporary worktree, `bb jvm-spec spec/isaac/agent/tool/comm_send_spec.clj` (1/18 failures). This is outside the bean's four streaming APIs/Grover/drive scope. Request planner decide whether acceptance may exclude this pre-existing JVM failure or coordinate separate repair; cannot honestly claim full `bb jvm-spec` green without unrelated change. Next: resume at `spec/isaac/agent/tool/comm_send_spec.clj:123` after planner resolution, rerun full suites and land when acceptance truly met.
