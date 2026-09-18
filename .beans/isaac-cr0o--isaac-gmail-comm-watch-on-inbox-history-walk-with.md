---
# isaac-cr0o
title: 'isaac-gmail comm: watch on INBOX, history walk with durable cursor + resync, thread→session, reply on thread'
status: in-progress
type: feature
priority: high
tags:
    - google
    - comm
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T19:34:18Z
parent: isaac-bv1l
blocked_by:
    - isaac-1jep
---

Gmail as a comm. Google tells Isaac "something changed" and Isaac finds out what.

## Inbound (isaac-gmail, handler for `:gmail/watch`)

- A push carries `emailAddress` + `historyId` only. Walk `users.history.list` from the **last processed history id** (durable, per host), `historyTypes=messageAdded`, `labelId=INBOX`; fetch each new message (`messages.get`, format full); apply the gate: allow-from senders (fail closed), label filters, skip drafts/sent.
- **Resync**: a history id older than Gmail keeps (~7 days, or a 404 from history.list) invalidates the cursor; fall back to `messages.list` on INBOX since the last known date and log `:gmail/resync`. Part of this bean, not a follow-up.
- Route: thread id → session (shared vocabulary), so a mail thread is one session; `:origin {:kind :gmail …}` carries thread id + message id + from/to/subject for the reply.
- Registration contributed to `:isaac.google/registration`: `users.watch` on INBOX to the shared topic, renewed weekly; the initial `historyId` from the watch response seeds the cursor. Scopes: gmail.readonly + gmail.send (or gmail.modify if labels are written).

## Outbound

- Reply path: `messages.send` with `threadId` + In-Reply-To/References headers so it threads in clients; unprompted send: to/subject/body. Plain text v1; HTML later.

## Scenarios (approved 2026-09-18, Micah) — committed `@wip` in isaac-gmail `269fb94` `features/comm/gmail/gmail.feature`

| line | scenario |
|---|---|
| :21 | a watch push after two new INBOX messages starts one turn per thread |
| :52 | a reply goes out on the originating thread with the headers clients need |
| :77 | an already-processed push starts nothing |
| :84 | a stale cursor resyncs from the inbox and continues |
| :107 | sent mail, label-only changes, and unknown senders never start a turn |

Pinned: cursor persisted at `google/gmail-cursor.edn` (per host); `history.list?startHistoryId=<cursor>&historyTypes=messageAdded&labelId=INBOX` (a 404 = cursor gone → `messages.list?labelIds=INBOX&q=after:<last-known>` resync, `:gmail/resync :from :to` warn, cursor = the newest message's historyId); `messages.get?format=full`; reply = `messages.send` `{:raw <base64url RFC 2822> :threadId}` with `To`, `Subject: Re: …`, `In-Reply-To`, `References`; session name `gmail-<threadId>`; drop reasons `:not-inbox :sender :self`. The watch registration (`users.watch` INBOX to `google.topic`, weekly) is contributed to `:isaac.google/registration` and covered by a unit spec — vo2q already proves the timer.

## Step ledger

| step | status |
|---|---|
| default Grover setup in … / config: / model responses queued / session … has transcript matching: / the session count is / grover records zero provider requests / an outbound HTTP request to … matches: / the log has entries matching: | reuse |
| the google auth store has access … and refresh … | reuse (6aw3) |
| no outbound HTTP request to {url} was made | reuse (vo2q NEW) |
| **the gmail history cursor is {id}** (Given seeds, Then asserts) | **NEW** |
| **the Gmail API history since {id} adds messages:** / **… since {id} contains:** (kind/id/threadId/labelIds rows) / **… since {id} is gone** (404) | **NEW — history.list stubs** |
| **the Gmail API returns message {id}:** (from/to/subject/message-id/body) / **the Gmail API inbox lists messages:** | **NEW — messages.get / messages.list stubs** |
| **Gmail pushes a watch notification with history id {id}** | **NEW — hands `{emailAddress historyId}` to the gmail handler, awaits turns** |
| **the sent mail decodes to:** | **NEW — base64url-decodes the last send's `raw`, matches headers + text** |

## Acceptance

Definition of done: `@wip` removed and

```
cd isaac-gmail && bb features features/comm/gmail/gmail.feature:21
cd isaac-gmail && bb features features/comm/gmail/gmail.feature:52
cd isaac-gmail && bb features features/comm/gmail/gmail.feature:77
cd isaac-gmail && bb features features/comm/gmail/gmail.feature:84
cd isaac-gmail && bb features features/comm/gmail/gmail.feature:107
cd isaac-gmail && bb ci
```

Unit specs for: history walk (cursor advance, dedupe, 404 → resync), the gate, RFC 2822 reply building, the watch registration entry (create/renew/expiry from the watch response).

Ops note: grant `gmail-api-push@system.gserviceaccount.com` publish on the topic before `users.watch` accepts it.

Dispatched: hail 316f0624 2026-09-18T19:33:38Z (band isaac-work)

## Checkpoint (scrapper@isaac-work-2)

Worktree `/Users/zane/agents/isaac/work-2/isaac-gmail-cr0o` `bean/isaac-cr0o` @ `bb3c2f1` (base origin/main@269fb94). Shared sibling `/Users/zane/agents/isaac/work-2/isaac-gmail` left on main.

**Done (units)**
- cursor persist `google/gmail-cursor.edn`
- gate: INBOX + allow-from fail-closed; :not-inbox / :sender
- history walk-page: messageAdded, skip label-only, 404 → resync
- RFC 2822 reply-raw (To, Re:, In-Reply-To, References)
- watch registration-entry weekly INBOX + seed cursor
- stub handler `handle-watch!` + manifest `:isaac.google/handler` `"gmail/watch"`, scopes readonly+send, version 0.1.1
- `bb spec spec/isaac/comm/gmail` 17/0

**Next**
1. Feature steps in `feature-steps/isaac/gmail_steps.clj` (cursor Given/Then, history/message stubs, watch push, sent-mail decode).
2. Wire handler to real Gmail HTTP + `run-turn!` with session `gmail-<threadId>` and origin `{:kind :gmail …}`.
3. Reply via messages.send using rfc2822.
4. Un-@wip `features/comm/gmail/gmail.feature`; `bb features` per-line then `bb ci`.

Resume: `feature-steps/isaac/gmail_steps.clj` + `src/isaac/comm/gmail/handler.clj`. Do not start other beans.
