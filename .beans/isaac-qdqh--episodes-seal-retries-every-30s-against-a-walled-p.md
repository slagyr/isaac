---
# isaac-qdqh
title: Episodes seal retries every 30s against a walled provider
status: draft
type: bug
priority: normal
created_at: 2026-10-02T04:41:34Z
updated_at: 2026-10-02T04:41:34Z
---

Found 2026-10-02 on skiff. `:episodes/seal-failed :reason :provider-error` at a ~30s cadence (:consecutive 1,2,4,8…) while the claude-code provider was over its subscription limit.

isaac-episodes worker.clj `tick!` runs every `default-tick-ms` (30000, line 17) over all open episodes; `report-seal-result!` (90-102) only throttles the log line; `lifecycle/maybe-seal!` → `segment/segment-span!` makes a fresh LLM call each tick and never consults agent's provider wall/weather.

## Wanted
Before sealing, check the gist provider's wall state; while walled, skip and back off until its retry-after, then resume.
## Acceptance (scenarios TBD)
- With the provider stamped `:wall`, a tick logs `:episodes/seal-deferred` and makes no provider call; attempts resume after retry-after.
