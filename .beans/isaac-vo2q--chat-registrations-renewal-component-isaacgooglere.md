---
# isaac-vo2q
title: Chat registrations + renewal component (:isaac.google/registration berth, pointer subscriptions per space)
status: draft
type: feature
priority: high
tags:
    - google
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:12:15Z
parent: isaac-bv1l
blocked_by:
    - isaac-0gtc
---

Chat stays subscribed without a human. Registration = a Workspace Events subscription per configured space (pointer-only, 7-day lifetime), created and renewed by a timer.

## Scope

- isaac-google: berth `:isaac.google/registration` — entries `{:create! sym :renew! sym :expiry sym :key sym}`; a `:isaac/component` timer that, per registration and per key (space / mailbox), creates when absent, renews when within N hours of expiry, and reads **expiry from Google** (`subscriptions.get`) rather than trusting local state. Registration state (subscription names, expiry) persisted per host.
- isaac-gchat: contributes one registration whose keys are the configured spaces; `events.subscriptions.create` with `targetResource` = the space, `eventTypes` = message created/updated/deleted, `notificationEndpoint.pubsubTopic` = the shared topic, no `payloadOptions.includeResource` (pointer). Scopes contributed to `:isaac.google/scopes` (confirm: chat.messages.readonly, chat.spaces.readonly).
- Adding a space = one config entry; the timer picks it up on the next tick (config hot-reload later).

## Scenarios to draft (stubbed Workspace Events API)

1. On start with two configured spaces and no subscriptions, two are created against the shared topic.
2. A subscription within the renew window is renewed; its new expiry is read back from Google.
3. A space removed from config has its subscription deleted.
4. Google refusing a create is logged with the reason and retried on the next tick (no crash loop).

Ops note (config, not code): the topic must grant `chat-api-push@system.gserviceaccount.com` publish rights before create succeeds.
