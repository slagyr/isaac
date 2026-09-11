---
# isaac-jrj0
title: Leg 2 — agent contributes its workers and resume/suspend as components; server stops calling the agent by name
status: in-progress
type: feature
priority: high
tags:
    - agent
    - server
    - component
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-11T15:05:10Z
parent: isaac-3q4m
blocked_by:
    - isaac-vs6f
---

Parent: isaac-3q4m (decision 4). Depends on leg 1.

Repo: **isaac-agent** (manifest entries), **isaac-server** (deletes the calls).

The agent's manifest contributes `:isaac/component` entries for the comm delivery outbox, the episodes worker and the turn queue (today all started by the server calling `isaac.comm.delivery.worker/start!`), plus one entry pairing the boot resume scan (start, ranked first) with suspend-all (stop, ranked last). isaac-server `app.clj` drops `worker/start!`, `worker/stop!` and both `session-store/registered-store` uses; `config/install.clj` drops the session-store registration (the agent registers its own store on activation).

Scenarios: none new (Micah 2026-09-11) — the existing worker and resume/suspend features already pin the behaviour and move with the code; "server boots without the agent and still serves HTTP" is a one-time acceptance check.

## Acceptance

Existing worker, episodes and resume/suspend features green after the moves; server boots without the agent module and still serves HTTP (one-time check); isaac-server app.clj and config/install.clj contain no isaac.session or isaac.comm.delivery requires (one-time check).

```
cd isaac-agent && bb features features/bridge/ features/episodes/ features/session/ && bb spec && bb ci
cd isaac-server && bb features && bb spec && bb ci
```

## Work checkpoint (2026-09-11, scrapper@isaac-work-1)

Done: agent Foundation components, manifest contributions, agent-owned store registration/resume/suspend, split worker lifecycle, and server-owned agent lifecycle deletion are committed and pushed on `bean/isaac-jrj0` in both repos. Focused agent/server specs are green; full server spec is green. Full agent spec had one unrelated/flaky file-tool failure and its focused rerun passed.

Next: run complete acceptance, verify server-without-agent HTTP boot and grep checks, then rebase both branches and hand off. Resume at `isaac-agent/src/isaac/agent/component.clj:16` if component behavior needs adjustment.
