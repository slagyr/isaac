---
# isaac-7dkp
title: 'Conversation routing seam: explicit thread target and origin-aware delivery'
status: completed
type: bug
priority: high
tags:
    - episodes
    - conversation
created_at: 2026-09-03T14:23:59Z
updated_at: 2026-09-03T17:32:38Z
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



## Runnable acceptance

Existing committed @wip contracts to activate/revise:

- isaac-agent `features/bridge/episode_dispatch.feature`: episode-thread first turn, warm turn, and chronicle regression. Extend its helper so the dispatch input is an explicit `:conversation {:kind :thread :id ...}` request, not a prebuilt charge with an ambiguous `:session-key`.
- isaac-discord `features/comm/discord/episodes.feature`: first/warm Discord replies use the inbound channel after the turn resolves to an episode.

Agent acceptance:

    cd isaac-agent
    bb features features/bridge/episode_dispatch.feature
    bb spec spec/isaac/bridge_spec.clj

Discord acceptance (after agent pin):

    cd isaac-discord
    bb features features/comm/discord/episodes.feature
    bb features features/comm/discord/routing.feature
    bb features features/comm/discord/reply.feature

Clean cutover acceptance: an episodes-crew thread that collides with a legacy chronicle must return a named conflict; it must not run that chronicle.

## Scenario plan (approved 2026-09-03)

1. An episodes crew routes an explicit thread to one backing episode, never a same-named chronicle.
2. A warm request on that thread reuses the episode.
3. A chronicle crew routes the same shape to its chronicle.
4. A legacy chronicle/thread collision is rejected, not silently mixed.
5. Discord keeps its origin channel for typing and reply after the episode route.

Decision is approved by Micah’s request to bean and implement the protocol/defmulti seam.



## Summary of Changes

Shipped in the 0.1.41 episodes train (2026-09-03): isaac-agent 13da406 (conversation router seam d5cb192, origin preserved for delivery 07a0810, :episodes/opened logs origin 2b18dca), isaac-discord ab935be (request-map dispatch with :conversation/:origin; origin-first reply channel; stale tool-name + dispatch-capture fixtures repaired), isaac-acp 0cd2677 (pin alignment). Module suites green (discord 61/0 + 42/0, acp 64/0, agent 738/0 + 1605/0). Field: marvin opens episodes from Discord (2026-09-03-1647-xsdf) and ACP (2026-09-03-1651-p80b), see isaac-6488. Remaining items (bridge @wip scenarios, collision rejection, Discord mode-awareness, router fork, charge schema) split to isaac-qgeu.
