---
# isaac-t4oj
title: "Preserve isaac.system slots across nested with-system bindings"
status: completed
type: bug
priority: normal
created_at: 2026-05-09T13:51:14Z
updated_at: 2026-05-09T16:11:50Z
---

## Description

After isaac-dwtj moved config, tool-registry, and active-turns into isaac.system, many feature paths that call system/with-system with only {:state-dir ...} started dropping the existing runtime slots. This leaves turns without the current tool registry and config snapshot, causing broad feature regressions (unknown tools, missing slash registrations, discord/session routing failures). Fix the nested system binding semantics or the affected call sites so temporary state-dir bindings preserve the already-initialized runtime slots.

## Acceptance Criteria

bb features green again; nested system/with-system usage that binds :state-dir does not discard existing :config, :tool-registry, or :active-turns state; tool, slash, discord, and cancellation feature paths that previously failed from missing runtime slots pass again

