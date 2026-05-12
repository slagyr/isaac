---
# isaac-lkiy
title: "Discord: reply via REST API"
status: completed
type: feature
priority: normal
created_at: 2026-04-20T23:07:24Z
updated_at: 2026-04-21T17:37:09Z
---

## Description

Milestone 4 of the Discord channel adapter epic. Send the crew's response back to the originating Discord channel via the Discord REST API (POST /channels/{id}/messages).

Scope:
- Basic text reply in the same channel as the inbound message
- Authorization header with the bot token
- Handle 429 rate limits (parse retry-after, wait, resume)
- Errors (401, 403, 5xx) surface as channel on-error events

Out of scope: typing indicator (bead 5), long-message splitting (bead 6), reconnect (bead 7). Messages over 2000 chars get truncated with '...' until bead 6 lands.

Depends on bead 3.

## Acceptance Criteria

1. Implement POST-to-Discord-REST on turn-end/text-chunk for Discord-originated sessions.
2. Format Authorization header as 'Bot <token>' (not 'Bearer').
3. On 4xx/5xx, log :discord.reply/http-error with channelId + status.
4. Rename the step 'the last provider request matches:' to 'the last outbound HTTP request matches:' in spec/isaac/features/steps/providers.clj. Update call sites in features/*.
5. Remove @wip from both scenarios in features/comm/discord/reply.feature.
6. bb features features/comm/discord/reply.feature passes.
7. bb features passes overall.
8. bb spec passes.

## Design

Implementation notes:
- Namespace: src/isaac/comm/discord/rest.clj (or rest-client.clj). Knows how to POST to Discord's REST API with the bot token; does not know about sessions or routing.
- Base URL: https://discord.com/api/v10 as a constant (e.g. api-base). Tests hardcode the full URL in assertions, so if someone accidentally changes the base, tests catch it.
- Authorization header: 'Bot <token>' format (NOT 'Bearer'). Discord's convention.
- POST /channels/{channel_id}/messages with JSON body {:content "..."} for v1. Other payload fields (embeds, reply references) not required.
- Triggered by on-text-chunk or on-turn-end through the Comm protocol impl — when the crew produces text for a Discord-originated session, the adapter posts the whole turn content back. Start with full-turn posting (not streaming chunks) — simpler, avoids out-of-order delivery.
- Error handling (v1): on 4xx/5xx, log :discord.reply/http-error with channelId + status + response body preview. Do not retry. No on-error channel event required for this bead — logging suffices.
- 429 retry-after handling: NOT in this bead. Parse + wait + retry belongs in a follow-up or unit spec. Feature scenarios here only cover happy path + error logging.
- Rename step family 'the last provider request matches:' → 'the last outbound HTTP request matches:'. Companion short-form steps in providers.clj may keep their names if they work generically, but ideally update to match.

Reuses:
- 'the URL ... responds with:' (tools.clj:195) for stubbing responses
- 'the log has entries matching:' for error assertion

New step (part of scope):
- Rename provider-request-matches → last-outbound-http-request-matches. Paths: method, url, headers.<name>, body.<field>.

