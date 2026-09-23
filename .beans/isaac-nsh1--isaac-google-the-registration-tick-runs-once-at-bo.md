---
# isaac-nsh1
title: 'isaac-google: the registration tick runs once at boot, then hourly — a restart must not wait an hour to reconcile subscriptions and send the first heartbeat'
status: in-progress
type: bug
priority: high
tags:
    - google
created_at: 2026-09-23T00:54:38Z
updated_at: 2026-09-23T00:54:38Z
---

## Why

isaac-an14 moved the tick to hourly. The scheduler's :interval trigger fires after a full period (scheduler/runtime next-time = now + ms), so after a restart the first reconcile and the first heartbeat come an hour later. On the yopp train that means the old per-space subscriptions stay for an hour before spaces/- replaces them.

## Change

component/start! runs the registration tick once right after scheduling (scheduler after! with 0 ms, or a direct call on the scheduler thread), then the hourly interval. Spec: start! causes one tick before any interval elapses; the hourly task is still scheduled.

## Acceptance

bb spec / bb features / bb ci green in isaac-google.
