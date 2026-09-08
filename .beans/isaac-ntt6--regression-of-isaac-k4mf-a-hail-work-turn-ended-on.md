---
# isaac-ntt6
title: 'Regression of isaac-k4mf: a hail work turn ended on an empty final response after 1258 tool calls and was marked delivered'
status: draft
type: bug
priority: high
created_at: 2026-09-08T13:34:08Z
updated_at: 2026-09-08T13:34:08Z
---

Repo: isaac-hail (delivery_worker) / isaac-agent (drive/turn). Reopens the isaac-k4mf contract: a hail-driven work turn must not silently complete on an empty terminal model response.

## Evidence (zanebot, agent 0.1.49 / hail 2d7cb55, 2026-09-07 01:27Z)
Hail 567b453a (tono-2fe1) on tono-work-1: turn 2026-09-06 20:08:50Z → 2026-09-07 01:27:09Z, `:turn/model-response-summary :tool-calls-count 1258 :executed-tools-count 1258 :assistant-content-chars 0 :error nil :provider grok`, then `:hail/turn-ended :outcome :delivered` and `:hail/delivered`. No handoff, no commit, no bean note; the bean stayed in-progress with 19 files (+1344/−626) uncommitted on the cochlea checkout's main branch for 30 h until a human looked. Same shape as isaac-vrtb on 2026-09-05 (one work turn, no handoff, 17 h idle).

## Why k4mf's guard did not fire (to confirm)
k4mf's investigation covered turns that ended with NO tool calls and empty content. This turn executed 1258 tools and then the model returned nothing — likely the crew's tool-loop-max (scrapper: 400) or the provider's own loop budget cutting the loop, with the drive treating the empty tail as a normal completion. The delivery worker must treat 'empty final content on a work-band turn' as :no-progress → re-bind for a continuation turn (not delivered, not dead-letter), and log `:hail/turn-no-handoff` with the executed-tools count.

## Scenarios (isaac-hail features/delivery.feature, @wip, to be drafted with Micah)
1. a work-band turn whose final response is empty after N tool calls is not delivered: the worker re-hails a continuation on the same session and logs :hail/turn-no-handoff
2. a work-band turn that ends with a handoff (bean tagged unverified) is delivered as today
