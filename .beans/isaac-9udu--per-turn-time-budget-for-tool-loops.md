---
# isaac-9udu
title: "Per-turn time budget for tool loops"
status: draft
type: feature
priority: low
tags:
    - "deferred"
created_at: 2026-04-30T03:50:13Z
updated_at: 2026-04-30T03:50:43Z
---

## Description

Wall-time per turn should be bounded independently of iteration
count or cost. A turn stuck on a single broken tool for 30 minutes
is bad UX even if it's cheap.

## Goal

Track wall-time spent in the tool loop; abort or wrap-up when a
configurable time budget is exceeded.

## Design questions

- Configuration scope: per-crew, per-session, per-call.
- Hard kill vs soft (let LLM wrap up).
- Interaction with cancellation (isaac-wa06): the time-budget abort
  should reuse the cancel pathway so a single mechanism kills the
  in-flight LLM HTTP and any running tools.

## Status

Deferred. Captures the design alongside cost-budget and
no-progress-detection siblings.

## Related

- isaac-wa06: ACP cancel doesn't abort in-flight HTTP/SSE
- isaac-x4j2: wrap-up turn at cap
- (deferred sibling) Per-turn cost budget
- (deferred sibling) No-progress detection

