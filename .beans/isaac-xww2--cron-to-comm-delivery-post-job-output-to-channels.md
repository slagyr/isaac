---
# isaac-xww2
title: "Cron-to-comm delivery: post job output to channels"
status: draft
type: feature
priority: low
tags:
    - "deferred"
created_at: 2026-04-22T15:18:15Z
updated_at: 2026-04-22T15:52:36Z
---

## Description

Extends cron jobs with delivery routing: when a job fires, its response can be posted to a configured comm (Discord, Slack, iMessage, etc.) rather than just sitting in the session transcript.

Shape:
- Job record gains optional :deliver-to field, e.g. {:comm :discord :channel-id 'C999'}
- When the job's turn completes, the response content is routed via the named comm.
- Failure handling: if the comm is unreachable (network down, rate-limited, etc.), the delivery retries via a queue with backoff — overlaps with the delivery-queue bead (isaac-0jy tier 1 item).

OpenClaw's zanebot uses this for its cron jobs — results posted to Discord channels. Without this, cron output lives only in the session transcript (durable but not visible unless someone checks).

Depends on isaac-xdlg (cron) and requires a comm adapter (Discord done; others still pending).

