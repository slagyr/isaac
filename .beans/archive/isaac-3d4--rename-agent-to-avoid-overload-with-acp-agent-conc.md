---
# isaac-3d4
title: "Rename 'agent' to avoid overload with ACP agent concept"
status: completed
type: task
priority: normal
created_at: 2026-04-13T01:37:53Z
updated_at: 2026-04-14T04:39:50Z
---

## Description

## Problem

'Agent' is overloaded. Isaac itself is an ACP agent. Isaac's personalities (main, ketch) are also called agents. Renaming personalities to 'crew' eliminates the collision.

## What changes

### Feature files (planner handles these)
- All steps: `the following agents exist:` → `the following crew exist:`
- All references: `agent "main"` → `crew "main"`
- CLI flags: `--agent` → `--crew`
- Table columns: `| agent |` → `| crew |`
- Transcript metadata: `message.agent` → `message.crew`
- File renames: `agents.feature` → `crew.feature`
- Command names: `isaac agents` → `isaac crew`
- @wip on all modified files

### What does NOT change
- `agentInfo` — ACP protocol field (means Isaac itself)
- `agentCapabilities` — ACP protocol
- `agent-id` in ACP server internals that reference the ACP agent (Isaac)

### Source code (worker implements)
- `src/isaac/config/resolution.clj`: `resolve-agent` → `resolve-crew`, `resolve-agent-context` → `resolve-crew-context`
- `src/isaac/cli/agent.clj` → `src/isaac/cli/crew.clj`
- CLI registration: `isaac agents` → `isaac crew`
- `--agent` option in acp.clj, chat.clj, toad.clj → `--crew`
- Config keys: `agents.defaults` → `crew.defaults`, `agents.list` → `crew.list`
- Step definitions: all agent-related Given/When/Then steps
- Storage: `~/.isaac/agents/` → `~/.isaac/crew/` (quarters)
- AGENTS.md references (but filename stays — it's a convention)

### Config (isaac.json)
```json
"crew": {
  "defaults": {"model": "ollama/qwen3-coder:30b"},
  "list": [{"id": "ketch", "model": "grok"}],
  "models": {...}
}
```

## Acceptance
- All @wip removed from feature files
- bb spec passes
- bb features passes
- `isaac crew` lists crew members
- `--crew ketch` works on acp and chat commands
- `~/.isaac/crew/ketch/SOUL.md` is the quarters path

## Acceptance Criteria

All @wip removed from feature files. bb spec and bb features pass. isaac crew works with implicit default. --crew flag works on acp and chat. Quarters at ~/.isaac/crew/. NEGATIVE: isaac agents returns unknown command. cli/agents.clj is deleted. No source file in src/ uses 'agent' to mean personality/crew (agentInfo/agentCapabilities exempted as ACP protocol).

