---
# isaac-bpr3
title: "Fix discord routing isaac file assertions"
status: completed
type: bug
priority: normal
created_at: 2026-04-23T19:15:36Z
updated_at: 2026-04-23T19:53:06Z
---

## Description

`bb features` currently fails in `features/comm/discord/routing.feature` on the scenario `message routes to the session recorded in the Discord routing table`. Investigate whether the renamed EDN isaac file steps now resolve routing.edn paths incorrectly or whether the Discord routing feature helper is reading from the wrong location, and restore the approved behavior.

## Notes

Fixed the renamed `EDN isaac file` step so non-config paths such as `comm/discord/routing.edn` resolve under `state-dir/` rather than `.isaac/`, and preserve string routing-table leaf values. Verified with `bb features features/comm/discord/routing.feature` and `bb spec spec/isaac/features/steps/server_spec.clj`. Broader `bb features` failures remain outside this bead: config hot-reload scenarios are tracked by isaac-3rez, and the config-composition malformed-EDN scenario is tracked by the new follow-up bead created in this pass.

