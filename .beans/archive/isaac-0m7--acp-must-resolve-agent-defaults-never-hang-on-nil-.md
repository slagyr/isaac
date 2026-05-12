---
# isaac-0m7
title: "ACP must resolve agent defaults, never hang on nil model"
status: completed
type: bug
priority: high
created_at: 2026-04-12T01:55:24Z
updated_at: 2026-04-12T02:08:04Z
---

## Description

## Problem

`isaac chat --toad` spawns `isaac acp` which spins forever when the user sends a prompt. Root cause traced via /tmp/isaac.log: the chat flow is dispatched with `:contextWindow nil :provider nil :model nil` and the streaming path hangs (or errors silently through a path that does not surface via ACP notifications).

## Root cause

`src/isaac/cli/acp.clj:build-server-opts` builds `agents`/`models`/`provider-configs` maps by reading `(get-in cfg [:agents :list] [])` — but real configs like ~/.isaac/isaac.json have no `:agents :list` key. Agents are implicit, resolved through `config/resolve-agent` which merges `:agents :defaults` with workspace SOUL.md.

`src/isaac/acp/server.clj:resolve-agent-model` then does a flat `(get agents agent-id)` against the empty map, producing all-nil fields that cascade into the chat flow.

The CLI chat path uses `config/resolve-agent` + `resolve-model-info` (src/isaac/cli/chat/loop.clj:40-51) which handles the defaults-only case correctly. ACP was written against a hypothetical `:agents :list` shape that does not exist.

## Invariant

A session must never exist without a resolved model. Defaults are a floor, not optional. If resolution ever returns nil, the request must fail loudly (error envelope per ACP spec), not hang.

## Fix

1. Factor the resolution chain out of `cli/chat/loop.clj:prepare` into a shared helper (e.g. `isaac.config.resolution/resolve-agent-context`) that returns `{:soul :model :provider :provider-config :context-window}`.
2. Update `cli/acp.clj:build-server-opts` (and `acp/server.clj:resolve-agent-model`) to use the shared helper.
3. Add a guard: if the resolved model is nil, throw a clear error the ACP layer turns into a per-request error envelope.

## Acceptance

- Run `bb features features/cli/acp.feature` — all three new @wip scenarios pass after removing @wip:
  - 'acp resolves main agent from config defaults when no agent list is configured'
  - 'acp falls back to hardcoded defaults when no isaac.json exists'
  - 'acp returns an error when agent resolution yields no model'
- Manual verification: `isaac chat --toad`, send 'Hello', Toad receives a streamed response.
- No regression in features/cli/chat.feature or features/acp/*.feature
- @wip removed from all three scenarios

## Acceptance Criteria

All three @wip scenarios in features/cli/acp.feature pass with @wip removed. isaac chat --toad with an unconfigured main agent produces a response in Toad. No regressions in chat or acp features.

