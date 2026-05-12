---
# isaac-tf22
title: "Rename internal agent symbols to crew"
status: completed
type: task
priority: deferred
created_at: 2026-04-23T01:33:21Z
updated_at: 2026-04-24T00:48:33Z
---

## Description

Half-finished internal rename from 'agent' to 'crew'. ACP protocol terms (agentInfo, agentCapabilities, 'ACP agent sends response') are external vocabulary and stay — this bead is about internal symbols only.

Rename targets (mechanical):
- Function/var names: agent-id → crew-id, configured-agents → configured-crew, resolve-agents → resolve-crew, print-agent-sessions → print-crew-sessions, build-cfg [agents models] → build-cfg [crew models]
- Opts keys: :agents (as a local map key in server/acp wiring) → :crew-members
- Local bindings: 'agents' → 'crew' where they hold the crew map

Touch sites (non-exhaustive):
- src/isaac/acp/server.clj
- src/isaac/cli/acp.clj
- src/isaac/cli/prompt.clj
- src/isaac/cli/sessions.clj
- src/isaac/server/acp_websocket.clj
- src/isaac/session/bridge.clj
- src/isaac/session/context.clj
- src/isaac/drive/turn.clj
- spec files that reference these symbols

Leave alone:
- ACP protocol fields: agentInfo, agentCapabilities, result.agentInfo.*, params.agentCapabilities.*
- Feature file text like 'the ACP agent sends response N:' (external protocol)

Acceptance:
1. grep -rn 'agent-id\|configured-agents\|resolve-agents\|print-agent-sessions' src/ returns no matches
2. bb features and bb spec pass
3. ACP protocol fields still serialize as agentInfo/agentCapabilities (unchanged externally)

## Notes

Completed mechanical internal rename from agent -> crew where targeted. Acceptance grep for agent-id/configured-agents/resolve-agents/print-agent-sessions is clean, and bb spec / bb features both pass with ACP agentInfo/agentCapabilities behavior unchanged externally.
Completed the remaining internal agent->crew rename work. Acceptance grep for agent-id/configured-agents/resolve-agents/print-agent-sessions is clean, and bb spec / bb features both pass with ACP external agentInfo/agentCapabilities unchanged.

