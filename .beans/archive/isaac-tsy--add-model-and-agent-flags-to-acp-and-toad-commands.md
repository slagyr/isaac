---
# isaac-tsy
title: "Add --model and --agent flags to acp and toad commands"
status: completed
type: feature
priority: normal
created_at: 2026-04-12T14:12:00Z
updated_at: 2026-04-12T14:38:02Z
---

## Description

## Problem

`isaac acp` and `isaac chat --toad` use the agent's default model with no override mechanism. Users need to switch models without editing config.

## Implementation

### ACP command (`src/isaac/cli/acp.clj`)
- Add `--model ALIAS` and `--agent NAME` to `option-spec`
- Pass model override through to the server opts / turn context resolver
- If `--model` alias is unknown, print error to stderr and exit 1

### Toad launcher (`src/isaac/cli/chat/toad.clj`)
- `build-toad-command` accepts model and agent opts
- Appends `--model` and/or `--agent` flags to the `isaac acp` subprocess command
- `chat.clj:run-toad!` passes the opts through from parsed CLI args

### Agent config
- A 'grok' agent is now configured in `~/.isaac/isaac.json` with model alias 'grok'
- `~/.isaac/workspace-grok/SOUL.md` has Captain Barnaby 'Wobbly' Ketch personality
- Usage: `isaac chat --toad --agent grok`

## Acceptance

- `bb features features/cli/acp.feature` passes with @wip removed from all three new scenarios
- `bb features features/chat/toad.feature` passes with @wip removed from both new scenarios  
- @wip removed from all five scenarios
- Manual: `isaac chat --toad --agent grok` launches Toad with Captain Ketch personality

## Acceptance Criteria

All five @wip scenarios pass with @wip removed. Manual verification: isaac chat --toad --agent grok works.

