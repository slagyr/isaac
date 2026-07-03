---
# isaac-qpp4
title: 'Verify loop: escalate to planner after N consecutive verify-fails instead of infinite worker bounce'
status: draft
type: feature
priority: normal
created_at: 2026-07-03T20:42:11Z
updated_at: 2026-07-03T20:42:11Z
---

## Problem (observed on isaac-iqqz, 2026-07-03)

The hail-bean-verify skill returns a failed bean to the WORK band. If the worker cannot resolve the failure (e.g. acceptance requires operator-only steps, or a genuine conflict), it re-hands to verify unchanged and the bean bounces worker <-> verify indefinitely. isaac-iqqz failed verification 3x identically with no progress and no escalation.

## Desired behavior

After N consecutive verify-fails on the same bean (suggest N=2), the verifier (or worker) escalates to the PLAN band instead of bouncing again — the planner decides: rescope acceptance, split the bean, mark blocked, or human-help. The loop must be bounded.

## Design questions to settle

- Where the counter lives: a bean tag/field (verify-fail-count) vs. thread-length heuristic via hail_get on the thread.
- Who escalates: verifier on emitting the Nth fail, or worker on receiving the Nth return.
- Interaction with first-fail/return-to-same-session process-test beans (those intentionally fail once) — count only CONSECUTIVE fails with no bean-body change between them.

## Scope

orchestration repo: hail-bean-verify / hail-bean-work skills (+ possibly a bean field). Draft until design settled + scenarios reviewed.
