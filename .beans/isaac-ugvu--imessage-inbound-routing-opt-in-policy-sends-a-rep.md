---
# isaac-ugvu
title: 'iMessage inbound routing: opt-in policy sends a reply to the crew that texted, or to an @crew prefix'
status: scrapped
type: feature
priority: normal
created_at: 2026-10-02T15:28:05Z
updated_at: 2026-10-06T20:22:53Z
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

## Decisions (2026-10-02, Micah)

1. TTL is 15 minutes (`:ttl-seconds 900`). The last sender wins.
2. An unknown `@name` is ordinary text: nothing is stripped, and the message goes to the default frequencies exactly as today.
3. Reply-mode hint: pending. Planner recommends scoping Red Alert's soul instead of adding a mechanism (see chat).

3. Reply-mode hint (Micah, 2026-10-02): not needed. Cron-specific behavior belongs in the cron prompt, not a crew's soul. Red Alert's soul is now personality only (the OpenClaw-era HEARTBEAT_OK rule moved out and was dropped), so a routed reply reaches a crew that answers like itself. isaac-cron already tells cron turns that the user may not see the reply.

All three open questions are settled. This is ready for scenarios.

## Implementation notes

- Read the clock through Isaac's clock (the features set it with "the current time is"), not System time.
- The work item gains `:crew` when routed; the dispatch charge carries it.
- New step: `comms.imessage.inbound-route is "<edn>"`, in the style of the other `comms.imessage.*` slice steps.
- The guard scenario "with no routing policy, a reply goes where it always has" already passes and must stay green.

## Acceptance

- isaac-imessage `features/comm/imessage/inbound_route.feature` — "a reply within the TTL goes to the crew that last texted that handle"
- isaac-imessage `features/comm/imessage/inbound_route.feature` — "after the TTL, a reply goes to the default session"
- isaac-imessage `features/comm/imessage/inbound_route.feature` — "a leading @crew routes to that crew and is stripped from the text"
- isaac-imessage `features/comm/imessage/inbound_route.feature` — "an unknown @name is ordinary text for the default session"
- isaac-imessage `features/comm/imessage/inbound_route.feature` — "a routed crew is told which message it is answering"
- The rest of the isaac-imessage features stay green, including "with no routing policy, a reply goes where it always has".

feature-baseline: isaac-imessage ee2a1f6514b59eaecb40c039fc91d35d03e3763c
feature-blob: isaac-imessage features/comm/imessage/inbound_route.feature a7378675bd420b51a24423f39df2136ef7c45b98 41
feature-blob: isaac-imessage features/comm/imessage/inbound_route.feature a7378675bd420b51a24423f39df2136ef7c45b98 63
feature-blob: isaac-imessage features/comm/imessage/inbound_route.feature a7378675bd420b51a24423f39df2136ef7c45b98 85
feature-blob: isaac-imessage features/comm/imessage/inbound_route.feature a7378675bd420b51a24423f39df2136ef7c45b98 96
feature-blob: isaac-imessage features/comm/imessage/inbound_route.feature a7378675bd420b51a24423f39df2136ef7c45b98 107

## Scrapped (Micah 2026-10-06)
Superseded by isaac/doc/design-conversations-and-comms.md: replies route to whoever owns the conversation (the opener of a new channel, the existing owner of an old one); other crews' sends into an owned channel land there as marked notes (isaac-mve9, iMessage part isaac-9khs). Its @wip scenarios were retired in isaac-imessage; the no-policy scenario stays as current behavior.
