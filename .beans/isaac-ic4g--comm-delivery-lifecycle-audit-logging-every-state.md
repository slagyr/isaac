---
# isaac-ic4g
title: 'Comm delivery lifecycle audit logging: every state transition logged'
status: draft
type: feature
created_at: 2026-07-03T17:41:02Z
updated_at: 2026-07-03T17:41:02Z
---

## Context (sister of isaac-jnkp)

The comm delivery layer logs exactly one event (:delivery/dead-lettered, ERROR). Enqueue, delivery, transient-failure/backoff are silent — diagnosing "why is there no Discord notification" (2026-07-03) came down to reading directory mtimes under comm/delivery/. Same audit disease isaac-jnkp fixes for hails; same cure.

## Design

INFO-level events for every state transition, structured fields (id, comm, target, attempts), namespaced consistently:

- :comm.delivery/queued — enqueued to pending (id, comm, target) [fires in the enqueue path used by comm_send]
- :comm.delivery/delivered — send succeeded, removed from pending
- :comm.delivery/attempt-failed — transient failure, attempts++, backoff scheduled
- :comm.delivery/dead-lettered — rename of existing :delivery/dead-lettered (clean cutover, no alias) + aligned fields

`grep :comm.delivery/ server.log` reconstructs any notification's journey.

## Likely repo scope

isaac-agent (comm/delivery/queue.clj, comm/delivery/worker.clj).

Draft until scenarios reviewed.
