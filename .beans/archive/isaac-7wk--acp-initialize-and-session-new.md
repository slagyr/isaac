---
# isaac-7wk
title: "ACP: initialize and session/new"
status: completed
type: task
priority: normal
created_at: 2026-04-10T21:09:45Z
updated_at: 2026-04-10T21:27:30Z
---

## Description

Implement the ACP handshake and session creation.

## Scope
- Handler for `initialize` method: return protocolVersion, agentInfo (name: isaac, version), agentCapabilities (loadSession: true, promptCapabilities: {text: true})
- Handler for `session/new`: generate new Isaac session key under the `acp` channel, e.g. `agent:main:acp:direct:<uuid>`, create session in storage, return sessionId
- Workspace cwd inherited from the process at startup (use System/getProperty 'user.dir')

Parent epic: isaac-new
Feature file: features/acp/initialization.feature (2 @wip scenarios)
Feature file: features/acp/session.feature (session/new scenario only, session/load belongs to a later bead)

## Acceptance
Remove @wip and verify each scenario passes:
- bb features features/acp/initialization.feature:11
- bb features features/acp/initialization.feature:27
- bb features features/acp/session.feature:18

Full suite: bb features and bb spec pass.

