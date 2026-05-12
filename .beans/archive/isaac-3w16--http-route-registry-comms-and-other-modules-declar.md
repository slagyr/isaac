---
# isaac-3w16
title: "HTTP route registry: comms (and other modules) declare routes via register-route!"
status: completed
type: feature
priority: normal
created_at: 2026-05-07T16:45:19Z
updated_at: 2026-05-07T19:19:15Z
---

## Description

Today, isaac.server.routes hard-codes every HTTP route (/acp at line 33, /status, /error, /hooks/*). When ACP becomes a comm-module — and as future modules add routes (webhook receivers, web UIs, status dashboards) — this hard-coding leaks module knowledge into server code.

Replace with a registry pattern symmetric with isaac.api/register-comm!:

- (server.routes/register-route! :get "/acp" handler)
- Modules call this from their -isaac-init
- isaac.server.routes/handler iterates the registry to dispatch

Open design choices:
- Where does the registry live? Likely isaac.server.routes (atom) or a new isaac.server.route-registry (mirrors isaac.comm.registry).
- How is opts-handlers (the lazy-handle that passes opts to certain handlers) generalized? Maybe registration takes a flag or a metadata key.
- What about wildcard / prefix routes (/hooks/*)? Registry needs to support them or keep the current fallback path for prefix dispatch.
- ACP migration: acp's -isaac-init calls register-route! for /acp; isaac.server.routes drops the hard-coded entry.

Should land after isaac-8hpd (ACP namespace move) so the registration call sits in isaac.comm.acp/-isaac-init alongside register-comm!.

## Acceptance Criteria

server/register-route! exists; modules can register HTTP routes from -isaac-init; ACP's /acp route registers via this seam (no longer hard-coded in isaac.server.routes); existing /status, /error, /hooks/* routes still work (either via the same registry or kept as built-ins, decision documented); bb spec and bb features green.

