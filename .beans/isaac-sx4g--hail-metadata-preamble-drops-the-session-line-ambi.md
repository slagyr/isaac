---
# isaac-sx4g
title: Hail metadata preamble drops the Session line (ambient identity supersedes)
status: draft
type: task
priority: normal
created_at: 2026-07-02T18:29:51Z
updated_at: 2026-07-02T18:29:59Z
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
