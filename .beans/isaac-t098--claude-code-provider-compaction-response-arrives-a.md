---
# isaac-t098
title: 'claude-code provider: compaction response arrives as raw stream-json and is logged as :llm-error (session blocked after 3)'
status: todo
type: bug
priority: high
tags:
    - claude-code
    - compaction
created_at: 2026-09-18T14:42:28Z
updated_at: 2026-09-18T15:03:10Z
---

Compaction over the claude-code provider (`:provider :claude`, Claude Code CLI driver) never succeeds: the CLI completes the summary call, but Isaac records the raw stream-json output as the error and marks the compaction failed.

## Evidence (zanebot, 2026-09-18 14:30Z–14:35Z, agent 0.1.71 / isaac-claude-code 0.1.11)

- `isaac prompt --session isaac-work-3 --with-model claude-opus -m "…"` via the remote CLI (in-server, keychain OK). Window 755k tokens, `models.claude-opus` configured `:context-window 200000`, so `should-compact?` fired: `:session/compaction-started :provider "claude"`.
- The claude CLI finished: `claude/driver-exit :exit-code 0`; its result JSON: `"is_error":false, "duration_api_ms":117994, "num_turns":2, "stop_reason":"end_turn", "total_cost_usd":4.0005, "output_tokens":9722`.
- Isaac then logged `:session/compaction-failed :error :llm-error :provider "claude"` whose `:message` is the CLI's raw stream-json, starting `{"type":"system","subtype":"init","cwd":"/","session_id":…,"tools":["mcp__isaac__memory__get",…` — i.e. the compaction path received the driver's undecoded stream (system init line, stream_events, final result) instead of the summary text, and treated it as an error. Session blocked after 3 failures (`:block {:reason :compaction-failed}`).
- Same on isaac-work-2 (the remote CLI connection dropped mid-turn but the server-side turn ran the same way).

## Why it matters

Compaction is the only way a session grown under a 1M-window model (Codex) becomes usable on a 500k-window model (grok). The claude-code provider is the subscription (no per-token cost) path Micah wants for that job; today only the Anthropic API `opus` model can do it (~$4–8 per compaction).

## Suspected seam

The compaction chat (`compaction-tools-opts` → `dispatch/dispatch-chat-with-tools`) goes through the claude-cli adapter in a mode where the LoopDriver's stream-json is not parsed into a `{:content …}` response (the driver only decodes when it "drives the tool loop"?), so the raw text lands in `:error`. Compare with the title side-call path, which does parse. See also isaac-jkx7 (opus drifts) and the yopp `provider-contract-violated {:reasoning {:summary "is required"}}` on the haiku title side-call — likely the same adapter contract gap.

## Scenarios to draft

1. Compaction with the claude-cli provider (fake CLI, stream-json fixture) splices the summary and logs `:session/compaction-spliced` (or the existing success event), not `compaction-failed`.
2. A driver `is_error:true` result is reported as `:llm-error` with the CLI's `result` text as the message, not the whole stream.
3. `@real` smoke (isaac-l7l4 style): a real `claude` compaction on a >200k window succeeds.

## Repro on zanebot

`isaac remote wss://<host>/cli --token … -- prompt --session <big-session> --with-model claude-opus -m "Reply OK."` with `models.claude-opus.context-window 200000` and a window > 160k tokens.

## Root cause (confirmed 2026-09-18 15:2xZ, plan)

Not a parse problem. `isaac.llm.api.claude-cli/failed?` (isaac-claude-code, ~line 323) treats a run as failed when exit ≠ 0 **or** `auth-failure?` matches — and `auth-failure?` (~line 314) runs `(?i)not logged in|please run /login|invalid api key|not authenticated|no credentials|unauthorized` over the CLI's whole stdout+stderr. A compaction's stdout is the stream-json carrying the model's summary; isaac-work-2/3 spent the night on the principals/auth beans, so their summaries contain "Unauthorized". Verified on zanebot: the one successful compaction run's log line has `is_error":false` and the word `Unauthorized` twice in the summary text. The regex matched model content → `error-response` → `{:error :llm-error :unavailable? true :reason :auth}` → `compaction-failed` ×3 → `:block`.

Origin/main `459a236` ("clip claude CLI error text") only shortens the message.

## Fix

`failed?` trusts the CLI's verdict: nonzero exit, OR the parsed `result` event has `is_error true`, OR the auth regex matches **stderr or the result's own error text** — never the summary/content body. Spec: fake-CLI run whose result text contains "Unauthorized" with `is_error false` is a success; a real auth failure (`is_error true`, "OAuth session expired") is still `:unavailable? :auth`.
