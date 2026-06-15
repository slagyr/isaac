---
# isaac-bju6
title: 'iiga(3): comm slots load+register with a Service; start opens the client (discord fix)'
status: todo
type: feature
priority: normal
created_at: 2026-06-15T21:31:34Z
updated_at: 2026-06-15T21:31:34Z
blocked_by:
    - isaac-n4dj
    - isaac-kbzd
---

Child of epic isaac-iiga. THE proof of the model. Three roles, no dual instance:
- Module on-load: no-op (or registers contributions).
- The comm impl contributes a Service via :isaac.server/service (e.g. a discord Service owning the client
  machinery; start connects, stop disconnects).
- The comm SLOT (Reconfigurable) on-load REGISTERS its config with that Service (opens nothing);
  on-config-change updates the registration; on-unload deregisters.
- The server STARTS the Service -> opens client(s) for the registered comm configs.

Move the resource-open (discord client/socket) OUT of comm-slot on-load INTO Service start.

Acceptance (write @wip Gherkin) — the epic's proof:
- "discord config loads without starting the client": discord configured + on classpath; on config load
  (isaac prompt / CLI) the slot loads and registers with the discord Service, but NO client connects
  (no :discord/connected, no socket, no :service/started).
- "server start opens the discord client": on `isaac server` start, the discord Service starts and connects
  for the registered config; on shutdown it disconnects.
