---
# isaac-z6bz
title: "Design review: Comm protocol API — every channel must implement every method"
status: draft
type: task
priority: low
tags:
    - "deferred"
created_at: 2026-04-28T23:25:22Z
updated_at: 2026-04-28T23:25:37Z
---

## Description

The Comm protocol (src/isaac/comm.clj) is starting to feel rigid:

  - 7 methods today: on-turn-start, on-text-chunk, on-thought-chunk,
    on-tool-call, on-tool-cancel, on-tool-result, on-turn-end,
    on-error.
  - Every implementation (cli, acp, discord, memory, null, the
    prompt CLI's CollectorChannel) has to enumerate all of them
    even when most are no-ops.
  - Adding a method (e.g. on-thought-chunk via isaac-aept) requires
    touching every deftype. CollectorChannel was missed; isaac-x34t
    captures the symptom.

## Worth considering in the review

- Multimethod with a no-op default — adders only override what's
  meaningful for their channel.
- An events-of-interest model (channel declares which events it
  cares about; runtime filters before invoking).
- A single dispatch fn taking an event map, vs N protocol methods.
- Whether some methods belong on a different layer entirely (e.g.
  on-error is more of a runtime concern than a comm-channel
  concern).
- Discord's on-text-chunk is a no-op while on-turn-end does the
  send — that asymmetry hints the abstraction may not match all
  channels equally well.

Out of scope for this bead: actual implementation. This is a
"sit down and design" task.

Defer until at least one more comm channel ships and we have a
broader sample of "real channels" to design against.

