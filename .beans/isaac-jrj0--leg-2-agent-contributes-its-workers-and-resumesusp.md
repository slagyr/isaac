---
# isaac-jrj0
title: Leg 2 — agent contributes its workers and resume/suspend as components; server stops calling the agent by name
status: draft
type: feature
priority: high
tags:
    - agent
    - server
    - component
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-11T05:26:16Z
parent: isaac-3q4m
---

Parent: isaac-3q4m (decision 4). Depends on leg 1.

Repo: **isaac-agent** (manifest entries), **isaac-server** (deletes the calls).

The agent's manifest contributes `:isaac/component` entries for the comm delivery outbox, the episodes worker and the turn queue (today all started by the server calling `isaac.comm.delivery.worker/start!`), plus one entry pairing the boot resume scan (start, ranked first) with suspend-all (stop, ranked last). isaac-server `app.clj` drops `worker/start!`, `worker/stop!` and both `session-store/registered-store` uses; `config/install.clj` drops the session-store registration (the agent registers its own store on activation).

Scenarios to plan: boot with the agent loaded starts the four entries in rank order; shutdown suspends turns before workers stop; boot without the agent starts none and the server still serves HTTP; the resume scan runs after the HTTP listener is up (or decide the rank).
