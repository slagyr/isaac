---
# isaac-2wr9
title: 'isaac-gchat outbound: reply in thread; send to space, DM by email, group DM'
status: completed
type: feature
priority: high
tags:
    - comm
    - google
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T21:04:33Z
parent: isaac-bv1l
blocked_by:
    - isaac-0gtc
---

Isaac speaks in Google Chat: reply in the thread a turn came from, and send unprompted to a space, a DM, or a group DM.

## Scope (isaac-gchat)

- Comm reply path: turn replies go to the origin space + thread (`spaces.messages.create` with `thread.name`, `messageReplyOption` so a new thread is not opened).
- Outbound send (`send-schema`, as iMessage declares): `:gchat/space` (spaces/…), or `:gchat/to` a user email → `spaces.findDirectMessage`, create with `spaces.setup` when absent; `:gchat/thread` optional. Group DMs are spaces to the API — no special case.
- Message cap + chunking like Discord (`:gchat/message-cap`, Chat's limit is 4096 chars); markdown → Chat formatting is a small translate step (bold/italic/code/links); tables degrade to code blocks.
- Sent as the Google user (same OAuth token). The echo drop from child 3 must ignore what this bean sends.

## Scenarios (approved 2026-09-18, Micah) — committed `@wip` in isaac-gchat `1756474` `features/comm/gchat/outbound.feature`

| line | scenario |
|---|---|
| :20 | a turn's reply is posted in the originating thread as the Google user |
| :37 | send! to a person resolves the DM space, creating it when absent |
| :55 | send! to a space accepts the configured name or the resource name, with an optional thread |
| :74 | a reply longer than the message cap is split at newline boundaries, in order |
| :97 | what Isaac sends does not come back as a turn |

Pinned: reply = `POST /v1/{space}/messages?messageReplyOption=REPLY_MESSAGE_FALLBACK_TO_NEW_THREAD` with `thread.name`; bearer = the google auth store access token (6aw3); `send-schema` keys `gchat/space` (configured `:name` or resource name), `gchat/to` (email → `spaces:findDirectMessage?name=users/<email>`, 404 → `spaces:setup` DIRECT_MESSAGE with that member), `gchat/thread`; `gchat/message-cap` (default 4096, Chat's limit) with Discord's newline-then-hard split; markdown → Chat formatting is a small translate step (bold/italic/code/links; tables → code block).

## Step ledger

| step | status |
|---|---|
| default Grover setup in … / config: / model responses queued / an outbound HTTP request to … matches: (with #index) / the session count is / the log has entries matching: | reuse |
| the google auth store has access … and refresh … | reuse (6aw3 NEW step, isaac-google steps on the classpath) |
| the Chat API returns message …: / Google Chat delivers a message event for … | reuse (0gtc) |
| **gchat outbound comm is registered** / **gchat comm send! is invoked with:** | **NEW — Discord's phrases, gchat impl** |
| **the Chat API has no direct message space with {email}** / **the Chat API creates space {name} on setup** | **NEW — stubs for findDirectMessage (404) and spaces:setup** |

## Acceptance

Definition of done: `@wip` removed and

```
cd isaac-gchat && bb features features/comm/gchat/outbound.feature:20
cd isaac-gchat && bb features features/comm/gchat/outbound.feature:37
cd isaac-gchat && bb features features/comm/gchat/outbound.feature:55
cd isaac-gchat && bb features features/comm/gchat/outbound.feature:74
cd isaac-gchat && bb features features/comm/gchat/outbound.feature:97
cd isaac-gchat && bb ci
```

Unit specs for: target resolution (name/resource/email), markdown → Chat text, chunking.

Dispatched: hail ddf88fe3 2026-09-18T20:21:04Z (band isaac-work)


## Resume (isaac-work-1, 2026-09-18 hail ddf88fe3)

Claimed. Worktree `/Users/zane/agents/isaac/work-1/isaac-gchat-2wr9` `bean/isaac-2wr9` @ `fba8c62` (base origin/main@`1706487`). Pushed.

Done:
- Unit: format (markdown→Chat, newline split), target (name/resource), chat-api create/find/setup, send! + on-reply thread. `bb spec` 35/0/69.
- Production: `src/isaac/comm/gchat.clj` send!/on-reply/on-cycle-start; `chat_api.clj` create-message!/find-direct-message!/setup-direct-message!; handler attaches live gchat comm to dispatch.

Next (resume here):
1. Feature steps in `feature-steps/isaac/gchat_steps.clj` — `gchat outbound comm is registered`, `gchat comm send! is invoked with:`, Chat API 404 findDirectMessage + spaces:setup stubs, google auth store Given, capture outbound HTTP via `-http!`.
2. Strip `@wip` on `features/comm/gchat/outbound.feature`.
3. Acceptance: `bb features features/comm/gchat/outbound.feature:20` (then :37 :55 :74 :97) then `bb ci`.
4. Manifest send-schema + message-cap + chat.messages write scope; version bump.

Do not hand off until acceptance green.


## Handoff (isaac-work-1, 2026-09-18 hail ddf88fe3)

Ready for verify. Status stays in-progress + tag=unverified. Do not land — verify lands.

- isaac-gchat `bean/isaac-2wr9` @ `515946a` (base origin/main@`1706487`). Pushed.
- Turn replies POST `/v1/{space}/messages?messageReplyOption=REPLY_MESSAGE_FALLBACK_TO_NEW_THREAD` with `thread.name`; bearer = google auth store access token.
- send! `:gchat/space` (configured name or resource), `:gchat/to` email → findDirectMessage / spaces:setup DIRECT_MESSAGE, optional `:gchat/thread`.
- Message cap (default 4096) newline-then-hard split; markdown → Chat (bold/italic/links; tables → code block).
- Echo drop still `:self` (child 3). Scope `chat.messages`. Version 0.1.2.
- `bb features features/comm/gchat/outbound.feature` 5/0/11; `bb spec` 35/0/69; `bb ci` 11 feature examples 0 fail (inbound + outbound).



## Landed on main (2026-09-18)

main-sha: isaac-gchat c73179bad0b5cd0f26cbda952b2bf36314795e77
