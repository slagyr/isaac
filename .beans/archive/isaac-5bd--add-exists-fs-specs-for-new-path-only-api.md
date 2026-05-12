---
# isaac-5bd
title: "Add exists? fs specs for new path-only API"
status: completed
type: task
priority: normal
created_at: 2026-04-16T04:12:07Z
updated_at: 2026-04-16T04:13:31Z
---

## Description

Why: the filesystem API is being redesigned around path-only public functions, and the semantics should be driven by tests. What: add failing specs for exists? covering both in-memory and real filesystem backends, establishing behavior for existing and missing paths without exposing backend selection in the public API.

