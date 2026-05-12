---
# isaac-r7w
title: "Centralize turn context resolution at session layer"
status: completed
type: feature
priority: high
created_at: 2026-04-12T03:54:57Z
updated_at: 2026-04-12T04:04:00Z
---

## Description

## Problem

Soul/model/provider resolution is duplicated across channels (chat/loop.clj:139, acp/server.clj:53, channel steps). Each channel independently resolves the same things, and each gets it subtly wrong — ACP missed workspace SOUL.md fallback, future channels will hit similar gaps.

## Design

Introduce a session-level resolver (e.g. `isaac.session.context/resolve-turn-context`) that takes a session key (or agent-id) and returns `{:soul :model :provider :provider-config :context-window}`. This is the single source of truth.

- Soul: read fresh each turn (agent config > workspace SOUL.md > hardcoded default)
- Model/provider: resolved from config defaults + agent overrides + alias/ref parsing
- Channels stop passing :soul/:model/:provider — they pass the session key and the resolver handles the rest
- `process-user-input!` calls the resolver internally

## SOUL.md read fresh each turn

Reading SOUL.md on every turn means edits take effect immediately for existing sessions. No reload mechanism needed.

## Acceptance

- `bb features features/session/context.feature` passes with @wip removed from all scenarios
- `bb spec` passes
- Channels (ACP, CLI) no longer resolve soul/model/provider independently
- @wip removed from all scenarios

## Acceptance Criteria

All @wip scenarios in features/session/context.feature pass. Channels delegate to the shared resolver. bb spec and bb features pass.

