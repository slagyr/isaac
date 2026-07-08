---
# isaac-l1vb
title: ACP /status content-match scenario is red on main (pre-existing)
status: todo
type: bug
priority: normal
created_at: 2026-07-08T23:05:24Z
updated_at: 2026-07-08T23:05:24Z
---

## Problem

`bb features features/comm/acp/slash_commands.feature` fails in scenario
`/status returns formatted markdown via ACP notification` with
`Expected truthy but was: nil`.

Confirmed **pre-existing on `origin/main`** (`isaac-acp` @ `8e71510`), not
introduced by isaac-o14c. Reproduced on both the o14c branch (`09cf9f3`) and a
clean `origin/main` checkout — identical failure, identical assertion.

## Root cause (surfaced 2026-07-08, prowl)

The `/status` scenario asserts on `the notification content matches:`, which
reads `:last-acp-notifications` and scans a `session/update` markdown chunk for
"Session Status", "Crew ... main", etc. But `emit-status-notification!`
(`src/isaac/comm/acp/server.clj:181`) emits `/status` output as a `chat/status`
notification (structured data), not as a `session/update` markdown chunk. So
`last-notification-content` (reads `:params :update :content :text`) is empty
and every pattern `re-find` returns nil.

The scenario and the server disagree about how `/status` surfaces output.
Either the server must also emit the markdown as a `session/update` chunk, or
the scenario must assert against the `chat/status` payload.

## Not isaac-o14c

o14c's method-scoped notification-set step is correct and green on the replay
proofs. This `/status` red is orthogonal: o14c only changed
`replay-transcript!`'s transcript source and the notification-set step (scoped
to listed methods; `chat/status` is unlisted in this scenario).

## Acceptance

- [ ] `bb features features/comm/acp/slash_commands.feature` green in isaac-acp.
- [ ] The `/status` scenario proves the actual status output the server emits
      (reconcile scenario vs `emit-status-notification!`).
