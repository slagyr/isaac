---
# isaac-khgy
title: 'Spike: claude-cli with native tool calling — isaac as the CLI''s MCP tool server'
status: completed
type: task
priority: normal
tags:
    - claude-cli
    - spike
    - tool-protocol
created_at: 2026-09-03T22:43:00Z
updated_at: 2026-09-03T23:07:34Z
parent: isaac-tuk1
---

Question (Micah, 2026-09-03): can isaac keep executing tools while Claude Code runs a native tool loop, and do we still get the full transcript? Spike run on zanebot with claude 2.1.231, an Anthropic API key, a stdio MCP server exposing two tools, and a capture proxy on ANTHROPIC_BASE_URL logging every raw API request. Scripts: ~/spike on zanebot (mcp_server.py, proxy.py, run.sh). The model calls themselves failed — the API key has no credit balance — so the loop-execution half is still unproven; everything below is from the captured requests.

## Findings (captured API bodies)

1. **System prompt is ours.** With `--system-prompt`, the API `system` field is exactly: one billing-header line, the sentence "You are a Claude agent, built on Anthropic's Claude Agent SDK.", then our text (160–235 chars total). No CLAUDE.md, no dynamic env/git sections, no skills — in normal mode, not just `--bare`. `--setting-sources ""` and `--disable-slash-commands` changed nothing further.
2. **One unavoidable injection:** the first user message gets a `<system-reminder>` text block carrying only `# currentDate` (≈250 chars). Present in every variant incl. `--bare`. Harmless; isaac should expect and strip it from persisted transcripts.
3. **Tools are native.** `--tools ""` removed every built-in; the `tools` array contained exactly `mcp__isaac__isaac_add` and `mcp__isaac__isaac_note` with their JSON schemas. So the model gets real tool_use with schema-validated arguments and a stop at each call — the structural fix for isaac-jkx7.
4. **Replay works.** `--input-format stream-json` accepted an assistant-role message; the next request carried `user, assistant, user` history verbatim. Each user message on stdin triggers a turn, so isaac feeds prior history first and the live prompt last. Structured tool pairs on replay untested (needs a live run).
5. **Request extras the CLI adds:** `thinking {:type adaptive}`, `output_config {:effort high}`, `max_tokens 64000`, `context_management` (clear-thinking edits only — NOT compaction). Mid-turn compaction would still be the CLI's auto-compact, outside isaac's control.
6. **Side call per invocation:** a claude-haiku title-generation request fires on every run ("Generate a concise, sentence-case title…"). Cost + latency tax; find the switch (`-n`? settings) or accept.
7. **`--bare` is incompatible with subscription auth:** it reads only ANTHROPIC_API_KEY ("OAuth and keychain are never read"). Isaac's claude provider rides OAuth, so `--bare` is out; per finding 1 it is also unnecessary.

## Still to prove (needs credits on the API key, or a logged-in claude on a host with keychain)

- tools/call reaches isaac's MCP server and the CLI feeds the result back (mcp.log shows tools/list only so far).
- stream-json output carries assistant tool_use blocks, user tool_result blocks, per-message usage, and thinking blocks — the transcript + per-cycle stamp.
- Replay of prior tool_use/tool_result pairs via stream-json input.
- Whether the CLI truncates large MCP results or writes them to disk.
- Cancellation: SIGINT/stdin close mid-loop, and what the event stream reports.

## Design sketch if it proves out

claude-cli provider v2: spawn `claude -p --tools "" --strict-mcp-config --mcp-config <isaac-mcp> --permission-mode bypassPermissions --input-format stream-json --output-format stream-json --include-partial-messages --no-session-persistence --system-prompt <soul>`; isaac-mcp exposes the session's allowed tools (names namespaced as mcp__isaac__<ns>__<name>); the drive consumes the event stream: text/thinking deltas → chatter/reckoning, tool_use → on-tool-call (execution already happened via MCP), per-message usage → last-input-tokens stamp, result → reply. Compaction between turns as today. Supersedes the fence protocol for Claude models; jkx7 remains as the hardening for the legacy path.



## Live results (2026-09-03 22:47–22:55, zanebot, subscription OAuth via a throwaway launchd user agent)

Auth: ssh cannot read the keychain, but a job bootstrapped into `gui/<uid>` can — same mechanism the isaac server uses. `launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.spike.claude.plist` ran the spike with OAuth; no API credits needed. (Cleaned up after.)

**L1 — native tool loop through isaac's MCP server: WORKS.**
- mcp.log: `tools/list`, then `tools/call isaac_add {a 17 b 25}` carrying `_meta.claudecode/toolUseId`.
- stream-json output, in order: `system init` (tools = the two MCP tools, mcp isaac:connected, model) → `assistant` [tool_use mcp__isaac__isaac_add] **with its own usage (input_tokens 816)** → `user` [tool_result toolu_…] → `assistant` [text "42"] **usage input_tokens 900** → `result` ("42", num_turns 2, total usage, total_cost_usd). Plus stream_event deltas (text + input_json_delta) with `--include-partial-messages`.
- Captured API requests confirm the loop: request 1 = our system + [reminder, prompt] with the two tool schemas; request 2 = same + assistant tool_use + user tool_result fed back verbatim.
- So: full transcript (assistant blocks, tool pairs by id, per-cycle usage = the missing claude-cli stamp), native schema-validated calls, hard stop per call.

**L2 — replaying prior tool pairs via stream-json input: LOSSY.** An assistant message with a tool_use block is accepted and forwarded, but a user message carrying our tool_result block is rewritten by the CLI to `[Tool result missing due to in…]` (it treats the pair as interrupted). The model still answered 7 (inferred from the input). Consequence: history replay must be text (as today) or the CLI process must stay alive across turns (stream-json multi-turn), restarted at compaction boundaries with the summary as text. Design choice for the provider v2 bean.

**L3 — large MCP result: CAPPED BY THE CLI.** A 200,000-char result was replaced with `Error: result (200,000 characters across 1 line) exceeds maximum allowed tokens.` (1,251 chars sent to the API). The model saw the error, not the data. Isaac's own output caps (tools.defaults max-bytes 32768) already keep results under this; keep them.

**Side effects to budget:** one title-generation call per invocation (`<session>…</session> Write the title…`, ran on sonnet in L1); the first-message `<system-reminder>` also carried a userEmail line under OAuth. Cancellation mid-loop not exercised.

Verdict: the MCP path is viable and structurally fixes jkx7's failure modes for Claude models. Remaining design work: long-lived process vs per-turn spawn (because of L2), MCP server surface in isaac-mcp exposing the session's allowed tools with the crew's ACL, event-stream → Comm mapping (chatter/reckoning/tool events/reply), and the title-call switch.



## Summary of Changes

Spike complete (see live results above). Outcome folded into epic isaac-tuk1: extraction isaac-jllj, loop-driver seam isaac-1sdl, tool registry/MCP bridge isaac-zocg, driver v2 isaac-5xn7.
