---
# isaac-0pb
title: "ACP initialize should include model and provider in agentInfo"
status: completed
type: feature
priority: normal
created_at: 2026-04-12T14:23:28Z
updated_at: 2026-04-12T14:37:25Z
---

## Description

## Problem

Toad has no way to display which model backs the conversation. The ACP `initialize` response currently returns only `{:name "isaac" :version "dev"}` in agentInfo. Front-ends like Toad, Zed, and IntelliJ have no model metadata to show.

## Fix

`initialize-result` in `src/isaac/acp/server.clj` should resolve the agent's model/provider via `config/resolve-agent-context` and include them in `agentInfo`:

```clj
{:protocolVersion 1
 :agentInfo {:name "isaac" :version "dev"
             :model "qwen3-coder:30b" :provider "ollama"}
 :agentCapabilities {...}}
```

The initialize handler needs access to the resolved agent config. Currently it's a static function — it will need the server opts (or resolved context) threaded through.

## Acceptance

- `bb features features/acp/initialization.feature` passes with @wip removed
- @wip removed from the new scenario
- Manual: `isaac chat --toad`, Toad displays the model name

## Acceptance Criteria

@wip scenario in initialization.feature passes with @wip removed. Toad displays model name.

