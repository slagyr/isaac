---
# isaac-cr0o
title: 'isaac-gmail comm: watch on INBOX, history walk with durable cursor + resync, thread→session, reply on thread'
status: draft
type: feature
priority: high
tags:
    - google
    - comm
created_at: 2026-09-18T04:12:15Z
updated_at: 2026-09-18T04:12:15Z
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

## Scenarios to draft (stubbed Gmail API)

1. A watch push after two new INBOX messages starts one turn per thread with sender/subject/body in the transcript.
2. A push whose history id is already processed starts nothing.
3. A stale cursor triggers resync and continues.
4. A reply is sent on the originating thread with the right headers.
5. Sent mail and label-only changes never start a turn.

Ops note: grant `gmail-api-push@system.gserviceaccount.com` publish on the topic before `users.watch` accepts it.
