---
# isaac-iiga
title: Service lifecycle — split load/unload (presence) from start/stop (running)
status: draft
type: epic
priority: normal
created_at: 2026-06-15T19:45:29Z
updated_at: 2026-06-15T19:45:29Z
---

## Problem
Lifecycle conflates two orthogonal concerns: PRESENCE (contributions/config registered — cheap, must be
safe on any invocation incl. CLI) and RUNNING (sockets/clients/threads — only valid when the server is up).
Today a comm slot opens its resource (e.g. the discord client) inside Reconfigurable/on-startup!, which fires
during config reconcile (presence). `isaac prompt` avoids spinning up discord only because it passes no
:registries to install! (`(when (seq registries) ...)`) — a convention, not a guarantee. One caller with the
wrong arg starts discord on a CLI run.

## Model — three roles, two axes
- Module -> on-load / on-unload (rename from on-startup/on-shutdown): contributions register/deregister. Always safe.
- Reconfigurable -> on-load / on-config-change / on-unload (rename on-startup! -> on-load; ADD on-unload):
  a component exists per its config slice. Presence, not running.
- Service -> start / stop (NEW): runtime machinery. Started only by the server at boot (topological order),
  stopped at shutdown (reverse).

A component can be BOTH: loads from config (cheap) AND the server starts its service. The discord slot loads
from :comms without opening a client; the server starts the client as a service.

## The seam
Modules register services via a new server-owned berth (:isaac.server/service). Contributions are inert data
(start/stop fns) gathered at load — CLI-safe. Only the server's boot resolves and invokes them; nothing else
processes the berth, so a service cannot start on CLI BY CONSTRUCTION. Reuse existing ordering (loader
topo-order / app/start!), don't hand-roll.

## Concrete changes
1. Rename Module on-startup/on-shutdown -> on-load/on-unload.
2. Reconfigurable: on-startup! -> on-load; add on-unload; keep on-config-change!.
3. New Service (start/stop) + :isaac.server/service berth in isaac-server.
4. Split comm-slot lifecycle: resource open (discord client/socket) moves from on-load into start;
   on-load only registers the slot.
5. Server boot starts services (topo); shutdown stops them (reverse).
6. Unify the duplicated turn-on machinery so comm reconcile runs in ONE place (server boot), not reachable
   from the agent runtime.

## Acceptance (the proof)
- isaac prompt with discord configured + on classpath: config LOADS (slot present, validated) but the client
  does NOT connect — no socket, no :service/started.
- isaac server with discord configured: service STARTS (connects) and STOPS cleanly on shutdown.
- No code path outside server boot can start a service.
- Existing slot load/reconfigure/evict-from-config behavior preserved.

## Decomposition (spin out as children when scoped)
(a) rename Module + Reconfigurable hooks; (b) Service protocol + :isaac.server/service berth;
(c) split comm-slot load/start; (d) dedup the reconcile machinery.
Pairs with the activation feature (isaac-shnq) — lazy activation is the LOAD side; this adds the START side.
