---
# isaac-q63
title: "Refactor fs API toward simplified forwarding functions"
status: completed
type: task
priority: normal
created_at: 2026-04-16T01:02:39Z
updated_at: 2026-04-16T04:13:31Z
---

## Description

Why this issue exists and what needs to be done: The filesystem API currently exposes protocol methods that require callers to pass the fs implementation explicitly, which leaks infrastructure concerns into clients. We want to evolve the API one function at a time toward simplified public functions like (fs/read-file path) that forward through *fs*, while also cleaning up method naming toward a better-shaped filesystem contract. Start with one operation under TDD, preserving behavior except where explicitly changed by specs.

