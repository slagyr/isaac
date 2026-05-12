---
# isaac-l2u1
title: "turn usage spec references missing clear-caches var"
status: todo
type: bug
priority: normal
created_at: 2026-05-12T04:42:26Z
updated_at: 2026-05-12T04:42:26Z
---

## Description

Why
Full bb spec is red outside isaac-yonq scope.

Observed behavior
spec/isaac/drive/turn_spec.clj fails to load with: Unable to resolve symbol: file-store/clear-caches!

Reproduction
Run bb spec or bb ci.

Notes
This surfaced while re-verifying isaac-yonq after sync. The failure is unrelated to core manifest/factory migration.

