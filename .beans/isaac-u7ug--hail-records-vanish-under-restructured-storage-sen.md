---
# isaac-u7ug
title: Hail records vanish under restructured storage — send returns id, no file anywhere
status: in-progress
type: task
priority: normal
created_at: 2026-08-23T17:12:07Z
updated_at: 2026-08-24T13:05:51Z
---

Observed 2026-08-23 (post isaac-k27z storage restructure era): 'isaac hail send --band isaac-work' returned id 1232eaed; minutes later the record existed in NO hail dir (pending/inflight/delivered/deliveries all checked, find+grep across ~/.isaac/hail empty) while a worker turn DID resume the bean — so delivery may have happened with the record cleaned, or persistence is racing. Either way the audit trail broke: a sent hail must be findable somewhere from send until archive (hails-never-die requires durable records). Diagnose-first: trace 1232eaed's lifecycle in the current hail module; pin the record-retention contract in scenarios. Layer: isaac-hail — no overlap with in-flight work.
