---
# isaac-kj14
title: A delivery note names attention as its sender, never an empty crew/session
status: in-progress
type: bug
priority: normal
created_at: 2026-10-07T13:58:05Z
updated_at: 2026-10-07T15:00:50Z
---

Yopp, 2026-10-07: an attention notice landed in Micah's DM session as `[thread:objr0dvo] [sent here by crew  from session ] Session observer :episodes failed…`. Attention deliveries carry no `:crew` / `:session` (agent `attention.clj` enqueues only comm, target, content), so the isaac-mve9 note printed blanks.

## Fix
- `attention/enqueue-attention!` adds `:origin {:kind :attention}` to its delivery record.
- The delivery worker names the sender from what the record has: `crew <crew> from session <session>` when present (cron, comm__send), else the origin kind (`attention`). A note never prints an empty crew or session.

## Acceptance (gated)
- The @wip scenario at the end of isaac-agent `features/comm/delivery/comm_continuity.feature` passes with @wip removed.
- An attention notice queued through `attention` carries `:origin {:kind :attention}` (spec or existing attention feature assertion).
- `bb ci` and `bb jvm-spec` green.

Likely repo scope: isaac-agent. Deploy: zanebot and yopp (agent pin).

feature-baseline: isaac-agent ed90b0e6c3a59962ff577c6b67c0cf1240cea3e1
feature-blob: isaac-agent features/comm/delivery/comm_continuity.feature a9a8f9b4d68cfcd83d6e85f11ef82b9a7d60ac42
