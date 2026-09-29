---
# isaac-1i1x
title: 'isaac-hail CLI: hail send crashes on a real host — no session store in the CLI process'
status: draft
type: bug
priority: critical
created_at: 2026-09-29T18:42:29Z
updated_at: 2026-09-29T18:42:29Z
---

Found 2026-09-29 smoking the zanebot deploy (hail e95274a, agent 6aa86a3; rolled back). `isaac hail send --band smoke --prompt …` from a shell on zanebot → `IllegalArgumentException: No implementation of method: :list-sessions of protocol: isaac.session.policy/SessionPolicy found for: nil`. Since isaac-ex4q, send resolves sessions at send time (Agent submit / frequencies resolver) and the CLI process has no registered session store. The feature suite runs the CLI in-process with a store registered, so it never saw this. `POST /hail/send` (in the server) works — 201 with a turn id. Crews and skills on zanebot call `isaac hail send` from the shell.

Fix direction: the CLI send must either run inside the server (proxy the send to the server like other server-backed commands) or bootstrap the session store it needs. A feature must exercise the packaged CLI path (separate process), not `main/run` in-process.
