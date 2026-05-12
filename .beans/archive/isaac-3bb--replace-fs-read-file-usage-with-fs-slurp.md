---
# isaac-3bb
title: "Replace fs/read-file usage with fs/slurp"
status: completed
type: task
priority: normal
created_at: 2026-04-16T05:27:17Z
updated_at: 2026-04-16T21:37:27Z
---

## Description

A new public fs/slurp API exists and old read-file usage should be removed from project code and specs where they refer to isaac.fs. Find all uses of fs/read-file, replace them with fs/slurp, and keep behavior covered by specs.

