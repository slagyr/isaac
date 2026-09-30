---
# isaac-87nv
title: 'Fleet pin sweep: every repo on foundation 98de59a+ and agent''s new main, coherent'
status: todo
type: task
priority: high
created_at: 2026-09-30T23:58:18Z
updated_at: 2026-09-30T23:58:18Z
---

Micah, 2026-09-30: after the namespace restructure (isaac-vyqs) leaves pin foundation 06d58b7 + agent f953042 (+ older sibling shas), while foundation main has moved to 98de59a (4eay, 82nx, 46ty, CI restore). `bb pins` requires each repo's direct pins to agree with what its pinned siblings themselves pin.

## Order (layers; each layer lands before the next starts)
1. isaac-agent: foundation (+ -spec, -test-support) → foundation main.
2. isaac-http: foundation → same sha, agent → agent's new main.
3. cron, episodes, google, cli-server, claude-code, discord, foreman, hail, hooks, imessage, worksite.
4. acp (episodes, http), gchat + gmail (google), handbook (cron).
5. mcp (acp), cli-proxy (acp, cli-server).
Each repo: every isaac-* pin → the sibling's current main, coherent with that sibling's own pins. isaac-server is a git mirror of isaac-http; skip it.

## Also, while touching them
- isaac-google: `isaac.google.steps` → `isaac.google.google-steps` (gherclj aliases step namespaces by last segment; bare `.steps` collides). Drop the explicit `-s isaac.google.steps` wherever the glob now matches (gchat, gmail).
- isaac-hooks: `isaac.hooks.steps` → `isaac.hooks.hooks-steps`; same cleanup.

## Acceptance
- Every repo's GitHub CI green on its new main, `bb pins` coherent in CI.
- A table in this bean listing each repo's final main sha and its foundation/agent pins.
Ungated; planner verifies.
