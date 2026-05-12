---
# isaac-bvl
title: "Redesign session identity: user-named, agent-independent"
status: completed
type: feature
priority: normal
created_at: 2026-04-13T02:04:44Z
updated_at: 2026-04-14T01:15:05Z
---

## Description

## Design

Sessions have user-chosen names (or auto-generated memorable names). The name is display-friendly. The id is a slugified version used for filenames and API references. Sessions are stored flat under ~/.isaac/sessions/, independent of any agent.

## Key decisions
- Session name → slugified id → filename (e.g. 'Friday Debug!' → friday-debug → friday-debug.jsonl)
- Duplicate ids rejected with error
- Auto-generated names for unnamed sessions
- Agent is per-turn metadata, not per-session identity
- Index stored as pretty-printed EDN
- No migration of old sessions — clean break

## Feature files (all @wip)
- features/session/identity.feature — 13 new scenarios defining the model
- features/session/storage.feature — rewritten for flat named sessions
- features/session/keys.feature — rewritten as session routing (channel tracking)
- features/context/compaction.feature — session names
- features/cli/acp.feature — session names in CLI
- features/acp/*.feature — session names in ACP protocol
- features/bridge/commands.feature — session names
- features/chat/*.feature — session names
- features/cli/*.feature — session names
- features/providers/**/*.feature — session names
- features/session/*.feature — session names
- features/channel/memory.feature — session names

## Step definition changes needed
- 'the following sessions exist:' (name column, creates sessions)
- 'the following sessions match:' (id column, asserts sessions)
- 'the session count is N'
- 'the most recent session is {name}'
- 'a session is created with a random name'
- 'a session is created with name {name}'
- Session storage: flat under state-dir/sessions/, EDN index
- Remove agent-scoped session paths

## Acceptance
- All @wip tags removed from all feature files
- bb features passes
- bb spec passes
- Sessions stored under ~/.isaac/sessions/

## Acceptance Criteria

All existing features updated to use named sessions. Storage is flat. Agent is per-turn metadata.

