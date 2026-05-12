---
# isaac-5pd
title: "Claude Code SDK provider - spawn claude subprocess for Max auth"
status: completed
type: task
priority: high
created_at: 2026-04-04T01:30:33Z
updated_at: 2026-04-04T01:39:23Z
---

## Description

Replace direct HTTP OAuth with Claude Code SDK subprocess, like meridian does.

## How Meridian Does It
- Uses @anthropic-ai/claude-agent-sdk query() function
- Spawns claude as a subprocess with clean env (strips ANTHROPIC_API_KEY etc)
- SDK handles OAuth auth internally using native Claude Max session
- Proxy translates between OpenAI/Anthropic formats

## Isaac Approach
Use bb process/shell to spawn claude CLI with the agent SDK:
- claude --sdk-mode or equivalent subprocess invocation
- Pass prompt via stdin or args
- Parse streaming JSON output
- Handle session management (resume, compaction)

## Provider Config
provider: claude-sdk (or anthropic-sdk)
No apiKey needed - uses native Claude Max auth

## Integration Points
- New provider in dispatch (chat.clj resolve-api)
- New LLM client: isaac.llm.claude-sdk
- Feature file: features/providers/claude-sdk/messaging.feature
- Unit specs: spec/isaac/llm/claude_sdk_spec.clj

## Replaces
The current OAuth direct-HTTP path (anthropic-oauth provider) which gets 429d.

## Research Needed
- Exact claude CLI flags for SDK/subprocess mode
- Output format (streaming JSON events?)
- How to pass system prompt, tools, model selection
- Session resume capability

