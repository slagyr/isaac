---
# isaac-1bq1
title: "isaac-gchat: reactions on the triggering message show Yopp's progress — \U0001F440 working, ✅ answered, ⚠️ failed, ⏳ parked — one reply per message, no status posts"
status: in-progress
type: feature
priority: high
tags:
    - gchat
    - comm
created_at: 2026-09-23T18:24:53Z
updated_at: 2026-09-23T18:25:55Z
---

## Decision (Micah, 2026-09-23)

One response per message, and it must be a new message so it notifies. No
status message, no trace in the answer — Yopp reads like a person. Progress
is shown with reactions on the triggering message, which the Chat API lets a
user add and remove and which do not notify. Verified live on yopp
2026-09-23: 👀 added to Micah's DM message, held 20 s, removed, ✅ added — the
glyphs changed on his screen, no notification anywhere. The current token
(chat.messages scope) already allows reactions.create/delete; no new scope.

## Change (isaac-gchat)

- Reaction lifecycle on the message that triggered the turn, driven by the
  comm hooks already firing: on-turn-start → 👀; on-reply (answer posted) →
  remove 👀, add ✅; on-turn-end with :ended-by :error → ⚠️; parked on weather
  (:provider-unavailable) → ⏳ (kept until the resumed reply lands, then ✅).
  Remove-then-add for every change (there is no reaction update). Reaction
  names are remembered per turn so removal is exact.
- A message Yopp heard but was not addressed by (mention-only room, no
  mention) gets no reaction.
- Reaction failures are logged once at debug and never fail the turn.
- Glyphs configurable per comm (`gchat/reactions {:working "👀" :done "✅"
  :failed "⚠️" :parked "⏳"}`), default as above; `gchat/reactions false`
  turns the lifecycle off.
- Details on demand is conversational: one line in isaac.comm.gchat.guidance
  — when asked what you did or how you know, summarise the tools you used
  from the transcript in plain words; otherwise never include a trace.

## Scenarios (outbound.feature, stubbed Chat API records reaction calls)

- addressed message → 👀 on start, then removed and ✅ after the reply posts.
- error turn → 👀 then ⚠️ (and the h5v8 notice, unchanged).
- parked turn → ⏳; resumed reply → ✅.
- heard-only message → no reaction calls.
- reactions disabled → no reaction calls, everything else unchanged.

## Acceptance

bb spec / bb features / bb ci green in isaac-gchat; one-time on yopp: a DM
shows 👀 while Yopp works and ✅ when it answers, with exactly one reply.

## Related

isaac-h5v8 (notices), isaac-acou (guidance namespace), isaac-pq0b (Discord
live rendering — a different renderer for the same hooks).
