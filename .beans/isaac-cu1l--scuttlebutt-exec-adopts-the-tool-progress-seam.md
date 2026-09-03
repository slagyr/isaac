---
# isaac-cu1l
title: 'Scuttlebutt: exec adopts the tool-progress seam'
status: draft
type: feature
priority: normal
created_at: 2026-08-30T22:48:18Z
updated_at: 2026-09-03T16:40:56Z
blocked_by:
    - isaac-5nxf
    - isaac-jarr
---

Repo: isaac-agent. After isaac-5nxf lands the handler-ctx :progress! seam (proven by the test__sounding mock), exec__run streams incremental stdout/stderr through it (line-buffered, capped). Scenario sketch: a queued exec of a multi-line command yields tool-progress events per line in the memory comm. Draft until scenarios are written.
