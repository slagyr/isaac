---
# isaac-mtf
title: "resolve-agent-context must include workspace SOUL.md fallback"
status: completed
type: bug
priority: high
created_at: 2026-04-12T03:37:23Z
updated_at: 2026-04-12T04:04:00Z
---

## Description

## Problem

`resolve-agent-context` in `src/isaac/config/resolution.clj:121` returns `:soul (:soul agent-cfg)` — which is nil when no explicit soul is in the agent config. The workspace SOUL.md fallback and the hardcoded default are missing.

Compare `cli/chat/loop.clj:139-141` which correctly does:
```clj
soul (or (:soul agent-cfg)
         (config/read-workspace-file agent-id "SOUL.md")
         "You are Isaac, a helpful AI assistant.")
```

This was missed when `resolve-agent-context` was extracted for isaac-0m7. Result: ACP sessions via Toad get raw Qwen with no system prompt — user saw 'I am Qwen, a large-scale language model' instead of Dr. Prattlesworth.

## Fix

In `resolve-agent-context`, add the same fallback chain:
```clj
:soul (or (:soul agent-cfg)
          (read-workspace-file agent-id "SOUL.md")
          "You are Isaac, a helpful AI assistant.")
```

Also add a spec for `resolve-agent-context` asserting the three soul resolution paths:
1. Explicit soul in agent config
2. Workspace SOUL.md when no explicit soul
3. Hardcoded default when neither exists

## New step definitions needed

- `Given workspace {agent-id} in {home} has SOUL.md:` (with doc string) — writes SOUL.md to `{home}/workspace-{agent-id}/SOUL.md`
- `Given isaac home {path} has no config file` (if not already implemented by isaac-0m7)

## Acceptance

- `bb spec` passes with new resolve-agent-context specs
- `bb features features/cli/acp.feature` passes with @wip removed from both new soul scenarios
- @wip removed from both scenarios
- Manual: `isaac chat --toad`, verify Prattlesworth personality appears

## Acceptance Criteria

resolve-agent-context specs pass, both @wip soul scenarios in features/cli/acp.feature pass with @wip removed. Prattlesworth personality active in Toad.

