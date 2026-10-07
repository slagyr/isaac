---
# isaac-4syt
title: 'gchat guidance: where the crew''s own replies went, and what a delivery note is'
status: completed
type: task
priority: normal
created_at: 2026-10-07T00:19:58Z
updated_at: 2026-10-07T00:24:26Z
---

Micah, 2026-10-07, from the second continuity test on yopp. With delivery notes now marked by thread (isaac-fm94), Yopp placed the delivery correctly, but claimed it "never answered" an earlier question in another thread. It had; its replies carry no thread marker. A gchat reply always posts into the thread of the message it answers (the marked line just before it), so the fix is to say so in the standing gchat guidance (`isaac.comm.gchat.guidance/TEXT`), not to prefix the crew's own replies with markers. Models copy patterns from their own past turns, and a marked reply would start leaking `[thread:…]` into what Yopp posts.

## Shape
Add to `guidance/TEXT` (exact wording; the scenario matches these phrases):
- "Your own earlier replies carry no thread marker: each one went into the thread of the message it answered, the marked line just before it."
- "A line that starts with [thread:…] [sent here by crew … from session …] is something another crew or job posted into that thread, not words of yours."
And replace "It is delivered back over the channel this message came from" with "It is posted back into the thread this message came from" (comms, never channels).

## Acceptance (gated)
- The @wip scenario in isaac-gchat `features/comm/gchat/inbound.feature` ("the guidance tells the crew where its own replies went…") passes with @wip removed; the existing "frames the triggered turn exactly once" scenario stays green.
- One-time: the word "channel" no longer appears in `guidance/TEXT`.
- `bb ci` green.

Likely repo scope: isaac-gchat. Deploy: yopp.

feature-baseline: isaac-gchat 8a75871af48bcf045f4f10a45cf5fd98c745366d
feature-blob: isaac-gchat features/comm/gchat/inbound.feature 56e2001e60dface1f985dcd5344e7071a0cd5ec2

## Landed on main (2026-10-07)

main-sha: isaac-gchat 75e4e95ef4991a791947c509860249b0c59b21e5
