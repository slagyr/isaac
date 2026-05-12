---
# isaac-zrq
title: "Replace fs/read-file usages with fs/slurp"
status: scrapped
type: task
priority: normal
created_at: 2026-04-16T05:19:29Z
updated_at: 2026-04-16T05:20:47Z
---

## Description

A new public fs API exists and legacy read-file usage should be migrated to slurp. Replace all call sites that use fs/read-file with fs/slurp, update specs accordingly, and keep behavior the same.

