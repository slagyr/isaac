---
# isaac-4b21
title: 'Bean Gate: snitch CI — gate every completed baselined bean on main'
status: draft
type: task
priority: high
tags:
    - process
    - beans
created_at: 2026-09-19T20:43:16Z
updated_at: 2026-09-19T20:43:16Z
parent: isaac-rmq6
blocked_by:
    - isaac-cy85
---

Child 4 of isaac-rmq6. Draft until isaac-cy85 lands.

New GHA workflow in this repo (not the frozen ci-tests.yml): on pushes to main touching `.beans/`, for each bean whose status became `completed` in the push and that has `feature-baseline`, clone the named modules over https (public repos; shallow at each `main-sha` suffices because the blob lines supply the "before" side) and run `bb bean-gate verify <id>`. First version fails the workflow and pings through ci-failure-hail (needs the `ISAAC_SERVER_AUTH_TOKEN` secret reset or a `ci` principal via isaac-xo5p — it 401s today). Warn when a baseline line's commit is authored from a worker session.
