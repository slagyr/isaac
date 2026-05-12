---
# isaac-nnns
title: "One-off cron jobs (:at) and completed-job pruning"
status: draft
type: feature
priority: low
tags:
    - "deferred"
created_at: 2026-04-22T15:18:08Z
updated_at: 2026-04-22T15:52:35Z
---

## Description

Extends the cron scheduler with one-off (non-recurring) jobs and an age-based pruning policy.

Shape:
- One-off jobs are CLI-created (not config-defined), stored entirely in <state-dir>/cron.edn with a new :at field holding an ISO-8601 instant.
- When a :at job's instant arrives, it fires once, marks status as :completed, and never fires again.
- Pruning rule: completed one-off jobs older than 14 days get removed from state on each scheduler startup + every ~6 hours.
- Config-defined recurring jobs are NEVER pruned (they're declarative and always active).

This bead couples :at + pruning because pruning only makes sense for one-offs (recurring jobs have no 'completed' state to age out).

Depends on isaac-xdlg (main cron bead).

