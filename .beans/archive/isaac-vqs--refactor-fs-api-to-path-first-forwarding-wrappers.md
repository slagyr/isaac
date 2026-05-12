---
# isaac-vqs
title: "Refactor fs API to path-first forwarding wrappers"
status: completed
type: task
priority: normal
created_at: 2026-04-16T01:03:59Z
updated_at: 2026-04-16T04:13:31Z
---

## Description

Refactor the isaac.fs API so callers use path-first functions like (fs/read-file "path") without needing awareness of the dynamic *fs* var. Combine this with incremental cleanup toward a better-shaped filesystem protocol, one function at a time, under TDD. Start by defining the contract in specs and introducing forwarding wrappers, then update call sites and implementations in small verified steps.

