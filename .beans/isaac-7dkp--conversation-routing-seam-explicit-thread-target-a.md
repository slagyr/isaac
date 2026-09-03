---
# isaac-7dkp
title: 'Conversation routing seam: explicit thread target and origin-aware delivery'
status: draft
type: bug
priority: high
tags:
    - episodes
    - conversation
created_at: 2026-09-03T14:23:59Z
updated_at: 2026-09-03T14:23:59Z
parent: isaac-51xy
---

Likely repos: isaac-agent, isaac-discord; ACP integration follow-up.

## Decision (2026-09-03, Micah)

Conversation mode is mutually exclusive. A surface passes a stable external thread handle plus delivery origin; the agent bridge alone chooses a chronicle or an episode backing session. A thread for an episodes crew must never be silently created or reused as a chronicle. Delivery must use the preserved origin, not reverse-map the resolved episode id.

## Design

Agent owns a ConversationRouter protocol and dispatches by [crew conversation-mode conversation-ref-kind]. The route executes before charge/build and returns the internal turn target. Discord passes {:conversation {:kind :thread :id "discord-<channel>"} :origin {:kind :discord :channel-id ...}}; ACP passes its sessionId as the same thread form.

Clean cutover: an episodes crew encountering a same-name legacy chronicle is an explicit conflict/cleanup case, never a fallback.

## Scope

1. Replace isaac-mrfu prebuilt-charge heuristic with the explicit bridge routing seam.
2. Retrofit Discord inbound routing and turn callbacks to use thread + origin; its episode feature becomes active.
3. Re-pin ACP to the resulting agent SHA and deploy all three modules; isaac-6488 records ACP field evidence.

Draft pending committed @wip bridge scenarios and an exact dependency update for isaac-gx2q.
