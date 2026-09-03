---
# isaac-khgy
title: 'Spike: claude-cli with native tool calling — isaac as the CLI''s MCP tool server'
status: draft
type: task
tags:
    - claude-cli
    - spike
    - tool-protocol
created_at: 2026-09-03T22:43:00Z
updated_at: 2026-09-03T22:43:00Z
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
