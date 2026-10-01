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

## Layer 1-2

| repo | new main sha | foundation pin | agent pin |
|------|---------------|-----------------|-----------|
| isaac-agent | c81bf0920b65e1558ab2f06d7af055713632bef0 | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | n/a |
| isaac-http | 4fdd535bb7231fa18d7812c6f0d5b2d35fab740c | 98de59aa49f3950b5f6d5189e383249c2a1861c0 | c81bf0920b65e1558ab2f06d7af055713632bef0 |

Both repos' GitHub CI green on the shas above. isaac-agent: `bb ci` (1840 spec examples / 819 feature examples, 0 failures) and `bb jvm-spec` (1840 examples, 0 failures) green on the first try with isolated HOME — the turn_store.feature timing flakes did not reproduce, so no rerun was needed. isaac-http: `bb spec`/`bb features`/`bb jvm-spec` all green (197/120/197 examples, 0 failures); `bb pins` fails locally on this machine (macOS) with an unrelated pre-existing error (`missing lex :model-exists? in :validations` composing the :crew schema when the standalone foundation CLI boots with no real `~/.isaac` root) — reproduced identically against the *old* pins and a from-scratch clone matching CI's exact layout, so it predates this bump and isn't platform-portable to CI; GitHub Actions (Linux) CI for both commits is green, `bb pins` included (silent/passing step).
