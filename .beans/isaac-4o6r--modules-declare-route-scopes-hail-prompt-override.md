---
# isaac-4o6r
title: 'Modules declare route scopes: hail (+prompt-override), cli (+cli/read via :read-only), acp, hooks, mcp'
status: draft
type: feature
priority: high
tags:
    - security
created_at: 2026-09-18T04:13:56Z
updated_at: 2026-09-18T04:13:56Z
parent: isaac-gym1
blocked_by:
    - isaac-bzgw
---

Child 3 of isaac-gym1 (principals epic). Blocked by child 1 (pin bumps to the isaac-http sha that carries `:scope`).

| repo | route(s) | scope | extra |
|---|---|---|---|
| isaac-hail | `POST /hail/send` | `:hail/send` | handler: `prompt` override (band template override) requires `:hail/prompt-override` via `require-scope!`; `reply_to`/session-direct frequencies allowed under `:hail/send` |
| isaac-cli-server | `GET /cli` | `:cli` | handler: command whose manifest is `:read-only` for this argv needs only `:cli/read` (kjzq hint); mismatch ⇒ stderr + exit 77 frame, no execution |
| isaac-acp | `/acp` (if still routed) | `:acp` | |
| isaac-hooks | hook routes | `:hooks` | |
| isaac-mcp | MCP routes | `:mcp` | |
| isaac-http | health/root | none ⇒ admin, except an explicit unauthenticated `/health` if one exists today (keep behaviour) | |

Each repo: manifest `:scope` on the route entry, one scenario per route ("a principal scoped X reaches /route; one without gets 403"), pin bump, version bump. Registry pins are a train step.
