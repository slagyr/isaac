---
# isaac-ktvs
title: Migrate config/berths_spec to isaac-foundation
status: completed
type: task
priority: normal
created_at: 2026-06-15T16:47:59Z
updated_at: 2026-06-15T16:59:13Z
---

Restore the dropped isaac.config.berths-spec (12 examples) into isaac-foundation. NOT host-side —
it tests berths/reconcile! against a FIXTURE module-index (an inline marigold.bridge comm berth), no
server.app. Ordinary fixture-based migration, same shape as nav/loader.

Baseline: isaac/spec/isaac/config/berths_spec.clj @ 09795481 (ns isaac.config.berths-spec, 12 it).
Source:   isaac-foundation/src/isaac/config/berths.clj
Target:   isaac-foundation/spec/isaac/config/berths_spec.clj

Porting notes:
- Baseline requires isaac.config.schema.root (agent-owned/gone from foundation) and isaac.schema.registered-in
  — rewire to foundation's current schema source (schema-base/schema-compose) or the config-marigold fixture,
  exactly as nav_spec resolved its schema.root reference.
- Neutralize any agent vocabulary in the fixture (chartroom/foundry-style), per the established fixture rule.

Acceptance (gate): bb spec -> file (it)==executed (no dead examples), 0 failures, SCRAP zero '(it) inside (it)',
12 examples faithful, exercising real berths/reconcile! against the fixture index (no fakes)."


## Implemented

Repo: isaac-foundation @ 4745b7e
File: spec/isaac/config/berths_spec.clj (ns isaac.config.berths-spec)
Verify: cd isaac-foundation && bb spec spec/isaac/config/berths_spec.clj
Result: 12 examples, 0 failures, 20 assertions. Full foundation suite: spec 745/0, features 56/0.

Rewires: config-schema/root -> schema-base/base-root (nav_spec pattern); inline fixture vocab neutralized (comms->signals, crew->station, helm/freq->relay/band, marigold.bridge->marigold.chartroom, marigold.longwave->marigold.skybeam). Exercises real reconcile!/install!/effective-root-schema/config-paths/claims-path? against fixture indexes -- no fakes.
SCRAP: zero (it)-inside-(it) [gate]. Reports 2 (describe)-inside-(describe) -- baseline idiomatic nesting, outside this gate, runs clean.
