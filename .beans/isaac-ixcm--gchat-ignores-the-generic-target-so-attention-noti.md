---
# isaac-ixcm
title: gchat ignores the generic :target, so attention notices to a Chat space dead-letter
status: todo
type: bug
priority: high
created_at: 2026-10-02T04:41:34Z
updated_at: 2026-10-02T04:41:34Z
---

Found 2026-10-02 on yopp. yopp's `:attention {:notify {:comm "gchat" :target "spaces/26gscqAAAAE"}}` (Micah's DM space, correctly configured). The "provider is broken" notice logged `:gchat.send/missing-target` then `:comm.delivery/dead-lettered :reason :permanent`, attempts 0.

isaac-agent attention.clj `enqueue-attention!` (36-42) builds `{:comm … :target … :content …}` with the generic `:target`; isaac-gchat gchat.clj `send!*` (~73) only reads `:gchat/space` (via `target/resolve-space`). delivery/worker.clj `record-target` (20-31) documents the generic `:target` as the fallback other comms honour; gchat never did.

## Wanted
gchat's send falls back to the generic `:target` when `:gchat/space`/`:gchat/to` are absent (mirror `record-target`).
## Acceptance (scenarios TBD)
- An attention notice enqueued with only `:target "spaces/X"` is delivered to that space (gchat feature).

## Acceptance (Micah approved 2026-10-02; gated)
- The 3 @wip scenarios in isaac-gchat `features/comm/gchat/outbound.feature` (generic :target fallback; :gchat/space wins; a queued :target-only record is delivered) pass with @wip removed.
- `bb ci` green.

feature-baseline: isaac-gchat 9b9dd10825637f721f20b6eb7036f405834115a8
feature-blob: isaac-gchat features/comm/gchat/outbound.feature 7fb90fea21a1df67b0c4bdcfe50c7cca381e862d
