---
# isaac-ktvs
title: Migrate config/berths_spec to isaac-foundation
status: todo
type: task
priority: normal
created_at: 2026-06-15T16:47:59Z
updated_at: 2026-06-15T16:47:59Z
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
