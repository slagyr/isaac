---
# isaac-niku
title: 'Foundation pre-work: reorder load-config-result — discovery before schema use'
status: completed
type: task
priority: normal
created_at: 2026-06-12T12:49:58Z
updated_at: 2026-06-12T14:18:27Z
parent: isaac-brth
---

Phase A step 8 of the isaac-foundation extraction (see isaac-brth reshaping
note). Pure internal restructure of config/loader.clj:1079-1162 (schema still
statically required at this step). Prerequisite for the :isaac.config/schema
contribution berth: the composed schema can only be built AFTER discover!
runs, so the load pipeline must read modules before conforming.

- [ ] Read raw root EDN without conform (only :modules is needed up front;
      modules cannot come from entity files).
- [ ] Run discover!.
- [ ] Then run conform / entity loading / semantic passes with the schema
      value threaded as a parameter (still schema/root for now).

Behavior-identical; the existing loader spec suite is the safety net.

## Acceptance

- bb spec and bb features green, no expectation changes needed.
