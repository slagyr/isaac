---
# isaac-sx4g
title: Hail metadata preamble drops the Session line (ambient identity supersedes)
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-07-02T18:29:51Z
updated_at: 2026-07-02T19:09:49Z
blocked_by:
    - isaac-s0ho
---

## Context

Once session identity (Session + Crew) is ambient in the cached system prompt, the hail delivery preamble Session line is redundant.

## Design (clean cutover)

- metadata-preamble (isaac.hail.delivery-worker) drops the Session meta-line.
- Keeps the genuinely per-delivery lines: Hail id, Thread, Reply-to, Submitter session, From crew, Data, Params.
- Update the hail-metadata feature assertions accordingly (delete the Session pattern, no back-compat).

## Likely repo scope

isaac-hail (delivery_worker.clj, features/hail-metadata.feature).

## Acceptance scenarios (committed @wip, 2026-07-02)

isaac-hail `features/hail-metadata.feature`:
- New @wip scenario: preamble carries per-delivery facts (hail id, thread) and does NOT match a `^Session:` line (existing not-matching step — no new steps).
- Cutover applied in same commit: 4 standalone Session assertions deleted; reach-one scenario retitled (binding proven by which session ran the turn). Submitter-session assertions retained.

Acceptance: after implementation, un-@wip the scenario; `bb spec` and `bb features` green in isaac-hail.
