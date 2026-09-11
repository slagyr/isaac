---
# isaac-igpj
title: iMessage watch snapshot uses local Apple ID as sender; inbound dropped
status: completed
type: bug
priority: high
created_at: 2026-08-27T19:18:32Z
updated_at: 2026-08-27T20:48:35Z
---

Likely repo: `isaac-imessage`.

## Problem

zanebot 2026-08-27T16:03:50Z: Micah texted main (`micahmartin@mac.com`, chat `any;-;micahmartin@mac.com`, chat.db row 521). Isaac logged `:imessage.intake/drop-sender` with `handle=assistantmicahmartin@gmail.com` and `chat-guid=""`. No turn ran.

`imsg history --json` on the same rowid is correct (`sender=micahmartin@mac.com`, `chat_guid=any;-;micahmartin@mac.com`). `destination_caller_id` is zanebot's Apple ID (`assistantmicahmartin@gmail.com`).

imsg watch.subscribe can emit a row before `chat_message_join` and `handle.id` exist. `decodeMessageRow` then fills empty `sender` from `destination_caller_id` (the local account). Isaac compared that to `:imessage/allow-from` and dropped it. Empty-string `chat_guid` is currently truthy, so it never hits the no-chat-guid path.

## Decisions (2026-08-27, Micah)

- Fix in **isaac-imessage**, not imsg. Paper over the watch snapshot.
- Re-fetch via imsg RPC (`messages.history`; `chats.list` when `chat_id` is missing). `messages.history` requires `chat_id`.
- Do **not** add the local Apple ID to allow-from.

## Behavior

- Blank `chat_guid` / `chat_identifier` / `sender` / `chat_id` are missing (`str/blank?`).
- A watch payload is **incomplete** when inbound (`is_from_me` false) and any of: missing chat identity, missing sender, or `sender` equals `destination_caller_id`.
- Hydrate **before** allow-from. One attempt.
  1. Positive `chat_id` → `messages.history` `{chat_id, limit}` and pick matching `id`.
  2. Else `chats.list` then `messages.history` on those chats until the rowid is found.
- Allow-from and `session-key` use the hydrated payload.
- Complete payloads must not call history/list.
- Hydrate success: `log/warn :imessage.intake/hydrated-watch` (`:message-rowid`).
- Still incomplete: `log/warn :imessage.intake/incomplete-watch`, drop. Not `:drop-sender`.
- Self-messages still drop before hydrate.
- `notification->work-item` stays pure. Hydrate lives in the watch handler. Feature `inbox is polled` must go through hydrate+filter so complete rows stay a no-op RPC.

## Specs

`spec/isaac/comm/imessage_spec.clj` — empty-string `chat_guid` is missing identity (same as nil). Incomplete? helper. Hydrate helper: history-by-chat-id; chats.list fallback; no RPC on complete payloads.

## Features (`isaac-imessage`, `@wip`)

`features/comm/imessage/watch_hydrate.feature`

New steps (none exist today):
1. `the imsg history for chat {id:int} is:` — stage FakeImsgClient `messages.history` rows
2. `the imsg chat list is:` — stage `chats.list`
3. `imsg did not receive method {method:string}` — assert FakeImsgClient `-request!` calls

Extend source-row mapping with `dest-caller` and `chat-id`. FakeImsgClient must return staged history/list, not always `{:ok true}`.

Marigold: Cordelia = remote sender; logbook = local Apple ID; skybeam = other remote.

## Acceptance

```
bb spec spec/isaac/comm/imessage_spec.clj
bb features features/comm/imessage/watch_hydrate.feature:12
bb features features/comm/imessage/watch_hydrate.feature:24
bb features features/comm/imessage/watch_hydrate.feature:40
bb features features/comm/imessage/watch_hydrate.feature:56
bb features features/comm/imessage/watch_hydrate.feature:70
```

Remove `@wip` from that feature when green. Existing intake/filter/routing features stay green.

## Out of scope

- Patching imsg (don't emit until joins exist; don't use destination_caller_id as inbound sender).
- Changing allow-from config on zanebot.
