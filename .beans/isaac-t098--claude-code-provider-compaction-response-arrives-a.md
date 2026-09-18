---
# isaac-t098
title: 'claude-code provider: compaction response arrives as raw stream-json and is logged as :llm-error (session blocked after 3)'
status: in-progress
type: bug
priority: high
tags:
    - claude-code
    - compaction
    - unverified
created_at: 2026-09-18T14:42:28Z
updated_at: 2026-09-18T16:44:05Z
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

## Fix landed on branch (2026-09-18, plan)

isaac-claude-code `bean/isaac-t098` @ **e61df08** (Release 0.1.12). `failed?` = nonzero exit ∨ result event `is_error` ∨ auth signature in the CLI's own text (stderr, result error text, or bare stdout when no structured result). Model content never consulted. Specs: 70/0 native (3 new: json success mentioning Unauthorized; stream-json success mentioning Unauthorized; is_error result carrying "OAuth session expired" → :auth). Deploying to zanebot + yopp by direct isaac.edn pin at Micah's request; registry pin follows verify.

## Second cause, same class (2026-09-18 15:26Z) — fixed in 53aa2bf

isaac-work-3's first Claude Code compaction (on e61df08) returned its summary, but the driver's `invoke!` took the `:mcp-failed` fence-fallback because `reply-contains-fence?` found the literal `<tool_call>` marker in stdout — the summary quotes the worker's earlier tool calls. The fallback retry then died in `parse-tool-calls` with `JsonParseException: Unexpected character ('.')` on a fence payload that was prose. Both are "model content mistaken for control":
- fence detection now applies only when the request offered tools (`(and (seq (:tools request)) (reply-contains-fence? result))`); a tool-less request's text is content in full (`success-response` takes the request);
- `parse-tool-calls` skips a fence whose payload is not a JSON object with `:name`.
Specs: 72/0 native. Branch `bean/isaac-t098` @ **53aa2bf** (two commits, both under Release 0.1.12).

Deploy state: yopp on 53aa2bf (restart 15:49Z, clean). zanebot on e61df08 (restart 15:12Z); 53aa2bf goes on once isaac-work-3's in-flight compaction finishes. isaac-work-2 compacted on Claude Code with e61df08: 753k → 51k tokens (15:43Z, ~30 min).

zanebot on 53aa2bf too (restart 16:06Z, resume requeued 2, clean). Both hosts now carry both fixes; isaac-work-3 compaction rerun launched 16:07Z on claude-opus.

## Verified in production (2026-09-18 16:2xZ)

isaac-work-3 compacted on claude-opus with 53aa2bf: 751,170 → 7,057 tokens (`✨ compacted`, `:session/compaction-completed :provider "claude"`). isaac-work-2 earlier with e61df08: 753k → 51k. Both hosts on 53aa2bf. Handing to verify: acceptance = `cd isaac-claude-code && bb ci` (72/0 native specs incl. 5 new), plus the three production compactions above as the @real evidence. Registry pin (modules.edn) to follow the squash.

Verify hail: b51b77d8 2026-09-18T16:24Z (band isaac-verify)


## Verify fail (attempt 1, 2026-09-18): squash onto origin/main conflicts src/isaac-manifest.edn (isaac-4o6r)

HEAD isaac-claude-code: 53aa2bf (bean/isaac-t098). Working tree: clean.

§1: no feature files in this bean (spec-only). Remaining checks ran.

Green: `bb ci` 72/0/236 native specs (3 @real pending) + 43/0/139 features.

Land: `git merge --squash bean/isaac-t098` onto origin/main 7490a20 would conflict. merge-tree: `changed in both` `src/isaac-manifest.edn`.

- origin/main 7490a20 (isaac-4o6r, landed this session) adds `:isaac.http/route` POST `/claude/turns/:id` `:scope :mcp`.
- bean/isaac-t098 @ 53aa2bf (base 459a236) only bumps `:version` "0.1.11" → "0.1.12".
- Bean is not based on current main (ancestor? no). Verifier does not resolve squash conflicts.

Do not land. Rebase bean/isaac-t098 onto origin/main 7490a20 (keep both the :mcp route and 0.1.12), then re-hand. Specs/CI were green on 53aa2bf; re-run `bb ci` after rebase.

## Rebased for verify fail 1 (2026-09-18 16:4xZ, plan)

`bean/isaac-t098` rebased onto origin/main 7490a20 (isaac-4o6r route kept alongside 0.1.12): now **6f86183, 5d5e2d2** (head 5d5e2d2). `bb ci` on the rebase: 75/0 native specs (3 @real pending), 43/0 features. Force-pushed. Hosts stay on 53aa2bf (same code, pre-rebase sha) until the squash lands; re-pin both to main then.

Verify hail: b6bd89f0 2026-09-18T16:43Z (band isaac-verify) — attempt 2 after rebase onto 7490a20


## Handoff (scrapper@isaac-work-2, verify-fail repair)

Rebased `bean/isaac-t098` onto origin/main@7490a20. Manifest keeps both isaac-4o6r's `:isaac.http/route POST /claude/turns/:id :scope :mcp` and this bean's `:version "0.1.12"`.

branch: bean/isaac-t098 @ a2e378c (base origin/main@7490a20) FF

**Acceptance**
- `bb ci` 75/0/244 native specs (3 @real pending) + 43/0/139 features
