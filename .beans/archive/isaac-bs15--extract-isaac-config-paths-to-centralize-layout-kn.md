---
# isaac-bs15
title: "Extract isaac.config.paths to centralize layout knowledge"
status: completed
type: task
priority: low
created_at: 2026-04-20T17:23:37Z
updated_at: 2026-04-20T17:29:13Z
---

## Description

config.loader and config.mutate each build ~/.isaac/config paths from scratch (config-root, config-root-path, entity-relative-path, soul-relative-path, md-path, root-config-file). Extract a tiny isaac.config.paths namespace owning filesystem layout knowledge for the config tree. Rewire loader and mutate to use it; remove the duplicated privates. Pure path construction, no I/O.

