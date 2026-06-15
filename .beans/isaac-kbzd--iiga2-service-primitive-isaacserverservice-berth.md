---
# isaac-kbzd
title: 'iiga(2): Service primitive + :isaac.server/service berth'
status: todo
type: feature
priority: normal
created_at: 2026-06-15T21:31:34Z
updated_at: 2026-06-15T21:31:34Z
---

Child of epic isaac-iiga. The new server-only turn-on primitive.

- New Service protocol: start / stop. Registered via a service FACTORY (locked: factory -> Service instance,
  mirrors comm slots). A Service may hold registrations (components register with it — see child 3).
- New server-owned berth :isaac.server/service, declared in isaac-server. Contributions are inert factory data,
  gathered at load (CLI-safe).
- Server boot: instantiate services, start them in module TOPOLOGICAL order. Shutdown: stop in reverse.
  Only the server processes this berth — nothing else can start a service.

Acceptance (write @wip Gherkin):
- "a module's service starts on server boot": fixture module contributes a service; on `isaac server` start, its
  start fires (assert :service/started); on shutdown, stop fires (reverse order).
- "a service does NOT start on CLI": same fixture; a CLI command (no server) gathers but never starts it
  (no :service/started).
- "services start in topological order": multiple services start in module topo order, stop in reverse.
