---
# isaac-xo5p
title: 'Rollout: mint principals for CI, iPhone, laptops, worker sessions, planner; swap secrets; retire :server :auth :token'
status: draft
type: task
priority: normal
tags:
    - security
    - ops
created_at: 2026-09-18T04:13:56Z
updated_at: 2026-09-18T04:13:56Z
parent: isaac-gym1
blocked_by:
    - isaac-auie
    - isaac-4o6r
    - isaac-2a2x
---

Child 5 of isaac-gym1 (end cap, ops). Blocked by children 2, 3, 4 and their train.

## Runbook (zanebot, admin shell — NOT from a crew; mint output is the secret)
1. `isaac server auth mint ci --scopes hail/send --expires <+90d>` → GitHub secret `ISAAC_SERVER_AUTH_TOKEN`.
2. `mint iphone --scopes hooks` → the iPhone hook config.
3. `mint micah-mbp --scopes cli,acp,hail/send` (and per laptop) → `~/.config/isaac.edn` `:cli :remote :token` (isaac-tvcg/gar0), editor ACP configs.
4. One per worker/verify session that must hail: `mint isaac-work-1 --scopes hail/send` … → each session's env; workers can no longer read an admin token.
5. `mint plan --scopes hail/send,cli/read` → `plan/orchestration/.env` (the planner dispatches with this).
6. Watch `auth list` last-used + the audit log until every client shows up under its own principal.
7. Remove `:server :auth :token`; confirm `:auth/legacy-token` no longer logs. **One-time acceptance** (not a permanent scenario): 24 h of audit log with zero requests attributed to `admin` from any client that should have its own principal.

Record which secret went where in this bean (names only, never values).
