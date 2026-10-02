---
# isaac-ugvu
title: 'iMessage inbound routing: opt-in policy sends a reply to the crew that texted, or to an @crew prefix'
status: draft
type: feature
created_at: 2026-10-02T15:28:05Z
updated_at: 2026-10-02T15:28:05Z
blocked_by:
    - isaac-qn4o
---

Likely repo: **isaac-imessage**. From the isaac-1hfe review, 2026-10-02. Opt-in: absent config keeps today's behavior exactly.

## Why

iMessage is one point-to-point pipe: one handle, one chat. Every inbound text
becomes session `imessage:<chat-guid>` on the default crew, so a reply to
Red Alert's birthday ping landed on zane, who had no idea what it answered
(1hfe's motivating incident). Discord multiplexes by channel; iMessage can't.

## Design

Policy on the iMessage comm slot, **off by default**:

```clojure
:comms {:imessage {:type :imessage
                   :imessage/inbound-route {:policy      :both   ; :session (default) | :last-outbound | :prefix | :both
                                            :ttl-seconds 600}}}
```

- **Outbound index.** After a successful send, isaac-imessage records
  `last-outbound[handle] = {:crew :session :text :at}` in its own state dir
  (survives restarts; needs no ledger). The crew comes from the stamped
  delivery record (isaac-qn4o).
- **Inbound order** in `notification->work-item`:
  1. `:prefix`/`:both`: text starts with `@<known-crew>` → strip the prefix,
     route to that crew.
  2. `:last-outbound`/`:both`: last outbound to this handle is within the
     TTL → route to that crew.
  3. Otherwise today's session.
- **Where a routed reply lands:** a per-crew session
  `imessage:<chat-guid>:<crew>` with that crew on the charge. Never the
  sender's own (cron/heartbeat) session.
- **Context:** the trusted block isaac-imessage already prepends gains one
  line when routed by last-outbound: "Micah is replying to your message:
  <last outbound text>". The routed crew answers over iMessage as today.
- **Off means off:** with no `:imessage/inbound-route` (or `:policy :session`)
  nothing is indexed for routing and every inbound text goes where it does
  today. A different multiplexing scheme can be another policy value later.

## Open questions

1. TTL default (600 s?) and whether a newer outbound from another crew
   replaces the routing target (lean: yes, last sender wins).
2. Unknown `@name`: deliver unchanged to the default session (lean), or
   strip it.
3. Should a routed crew without iMessage-friendly instructions (e.g. Red
   Alert's terse soul) get a reply-mode hint, or is the trusted block enough?

Scenarios to be written after these are settled.
