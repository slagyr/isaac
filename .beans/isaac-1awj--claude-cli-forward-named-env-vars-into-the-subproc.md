---
# isaac-1awj
title: 'claude-cli: forward named .env vars into the subprocess env'
status: draft
type: feature
priority: high
created_at: 2026-08-28T17:17:47Z
updated_at: 2026-08-28T17:17:47Z
---

Likely repo: **isaac-agent** (`isaac.llm.api.claude-cli/subprocess-env` + provider schema).

## Problem

`claude` is a child process. It sees `System/getenv`, not `<isaac-home>/.env`.
Isaac already reads `.env` for `${VAR}` in config. LaunchAgent children do not
get `.env`, which is why zanebot had `CLAUDE_CODE_OAUTH_TOKEN` hand-written
into the plist. `isaac service install` drops that. Secrets must not live in
the plist (`launchctl print`).

## Design (Micah 2026-08-28)

Spawn-copy, not plist-copy. Names on the claude **provider** (`:forward-env`,
next to `:command` / `:extra-args`). Values from `isaac.config.env/env` at
spawn. Plist stays PATH-only. `service install` unchanged.

1. Schema default `["CLAUDE_CODE_OAUTH_TOKEN"]` so a bare `config/providers/claude.edn` works. Explicit `:forward-env []` opts out.
2. Missing name → omit the key, do not set empty.
3. `ANTHROPIC_API_KEY` still stripped even if listed (isaac-kn7y).
4. Precedence is `env/env` as-is (OS env wins over `.env`, same as `${VAR}`). Remove the hand-written plist token so `.env` is what the child sees.
5. `.env` snapshot is locked at config load; `isaac service restart` picks up a new token.

Out of scope: macos plist EnvironmentVariables, forwarding to other subprocesses.

## Scenarios (plan approved, gherkin in chat)

`features/llm/api/claude_cli.feature` (4):

1. Default: CLAUDE_CODE_OAUTH_TOKEN from .env is in the child env (no :forward-env in the provider file)
2. An unlisted .env secret is not in the child env
3. A name listed in :forward-env is in the child env
4. ANTHROPIC_API_KEY is stripped even when listed in :forward-env
