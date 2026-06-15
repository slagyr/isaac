---
# isaac-dw0g
title: Migrate config/configurator_spec to isaac-server
status: todo
type: task
priority: normal
created_at: 2026-06-15T16:47:59Z
updated_at: 2026-06-15T16:47:59Z
---

Restore the dropped isaac.config.configurator-spec (6 examples) into isaac-server. It (:require
[isaac.server.app]) — needs server's app wiring — and both configurator.clj and server.app live in
isaac-server, which already boots them. Restore into isaac-server, not a new host.

Baseline: isaac/spec/isaac/config/configurator_spec.clj @ 09795481 (ns isaac.config.configurator-spec, 6 it).
Source:   isaac-server/src/isaac/config/configurator.clj (+ isaac/server/app.clj)
Target:   isaac-server/spec/isaac/config/configurator_spec.clj
Covers:   component reconciliation (start/stop/reconfigure components as config slices change) + schema ownership.

Porting notes:
- Rewire the isaac.config.schema.root reference to the current schema source if needed (cf. nav/berths).
- Port to current behavior; flag real regressions, don't paper over.

Acceptance (gate): bb spec -> file (it)==executed, 0 failures, SCRAP zero '(it) inside (it)', 6 faithful,
exercising the real configurator against server.app (no fakes)."
