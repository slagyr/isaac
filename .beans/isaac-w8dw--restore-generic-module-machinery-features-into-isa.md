---
# isaac-w8dw
title: Restore generic module-machinery features into isaac-foundation
status: todo
type: task
priority: normal
created_at: 2026-06-15T17:03:15Z
updated_at: 2026-06-15T17:03:15Z
---

Two baseline features/module/ files testing the GENERIC module machinery (not a specific extension kind).
Foundation owns both: src/isaac/module/loader.clj (activation) and src/isaac/config/schema_compose.clj
(composition), and carries fixture modules (marigold.comm.parlor, marigold.providers.fizz).

Baseline (@ 09795481), features/module/ -> isaac-foundation:
- activation.feature          — "Module activation"
- schema_composition.feature  — "Module schema composition"

Restore each .feature + step-defs + helpers, exercised with foundation's fixture modules. Remove @wip,
green via bb features.

Acceptance: both green under isaac-foundation bb features, no @wip/pending, real loader/schema-compose
machinery (no faked steps)."
