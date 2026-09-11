---
# isaac-uwmg
title: Non-module library version conflicts across modules
status: draft
type: task
created_at: 2026-06-19T15:44:56Z
updated_at: 2026-06-19T15:44:56Z
---

REMINDER (draft, later). isaac-92p3 + the modules-list-conflict-warning bean
handle version conflicts between MODULES. But modules also pull ordinary
LIBRARIES (non-isaac, no manifest) via deps.edn, and two modules can pull
conflicting versions of the same library (e.g. cheshire, http-kit). tools.deps
resolves to one, but a silently-wrong lib version can break a module at runtime.

Later: decide whether/how to detect + surface non-module library conflicts
(report in `modules list`/a doctor command, fail-fast on known-incompatible
ranges, or rely on the release/BOM to pin shared libs). Parked until module-
level conflicts (92p3 + list-warning) land and we see real cases.
