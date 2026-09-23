---
# isaac-nsh1
title: 'isaac-google: the registration tick runs once at boot, then hourly — a restart must not wait an hour to reconcile subscriptions and send the first heartbeat'
status: completed
type: bug
priority: high
tags:
    - google
created_at: 2026-09-23T00:54:38Z
updated_at: 2026-09-23T00:58:21Z
---

## Why

isaac-an14 moved the tick to hourly. The scheduler's :interval trigger fires after a full period (scheduler/runtime next-time = now + ms), so after a restart the first reconcile and the first heartbeat come an hour later. On the yopp train that means the old per-space subscriptions stay for an hour before spaces/- replaces them.

## Change

component/start! runs the registration tick once right after scheduling (scheduler after! with 0 ms, or a direct call on the scheduler thread), then the hourly interval. Spec: start! causes one tick before any interval elapses; the hourly task is still scheduled.

## Acceptance

bb spec / bb features / bb ci green in isaac-google.

## Landed on main

main-sha: isaac-google e5686bd (version 0.1.9 at 6fbd5d7)

Planner-implemented and verified 2026-09-22: component spec 8/0, `bb spec` 212/0, `bb features` 33/0, `bb ci` green. start! queues one :delay tick (:google/registration-boot) ahead of the hourly interval; stop! cancels it too. gchat 0.2.1 (bb95b84) and gmail 0.1.6 (a193a5c) repinned to google 6fbd5d7, suites green.
