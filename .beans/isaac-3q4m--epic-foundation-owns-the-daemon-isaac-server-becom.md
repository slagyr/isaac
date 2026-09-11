---
# isaac-3q4m
title: 'Epic: foundation owns the daemon; isaac-server becomes isaac-http; the server stops knowing the agent'
status: todo
type: epic
priority: high
tags:
    - epic
    - foundation
    - server
    - agent
created_at: 2026-09-11T05:22:32Z
updated_at: 2026-09-11T05:22:32Z
---

## Why

isaac-server is three things wearing one coat: the HTTP listener, the isaac
daemon (boot phases, ranked services + supervisor, `isaac service` for
launchd/systemd), and a grab-bag of agent knowledge it reaches into by name
(the agent's workers, session store, MCP turn registry, comm types). It also
carries six stale June-14 copies of the agent's comm namespaces that load
second only by luck. A fresh box that installs only the server silently gets
the agent the server's deps.edn pins (0.1.46) instead of the registry's.

## Decisions (2026-09-11, Micah)

1. **Foundation owns the daemon.** Boot phases, the process runner behind
   `isaac server`, the ranked start/stop registry + supervisor, and the OS
   service manager (`isaac service install/start/stop/restart/status/logs`,
   launchd + systemd) move from isaac-server to isaac-foundation. Foundation
   knows how to run things it does not understand; modules are the things.
   Rationale: foundation already owns module activation order and the
   scheduler's own start/stop; a UDP/SMTP/ping server module must not depend
   on HTTP to get a lifecycle; an `isaac-host` module would be a pin that
   never earns its train hop.
2. **"Component", not "service", for in-process parts.** `isaac service`
   keeps its name (OS-level, like `brew services`). The in-process berth is
   `:isaac/component`, declared by foundation, keeping the existing entry shape
   `{<id> {:namespace <sym>}}` (the namespace implements start/stop, optionally
   Supervised); start order is module topological order, stop is the reverse;
   Stuart Sierra's Component is the Clojure word for exactly this shape (start/stop/rank); "node" is taken by berth-backed config instances, "service" stays with the OS command.
3. **HTTP is one component.** isaac-server contributes its listener as a
   component entry by manifest, keeps the route berth, auth and the generic
   config-berth reconcile. Nothing else.
4. **The agent contributes, never depends.** Its workers (comm outbox,
   episodes, turn queue) and its resume/suspend pair are component entries in
   its manifest (resume ranks first to start, suspend last to stop). The
   agent's deps never name the server. hooks depending on the server is fine.
5. **The MCP route belongs to the claude-code module**, renamed to say so
   (`/claude/turns/:id`); the per-turn registry stays an agent library.
6. **The server knows no comms.** Comm-type validation becomes an
   `:isaac.config/check` contribution from the comm-owning side; the six stale
   copies (comm/{protocol,memory,null,render,delivery/queue,delivery/worker})
   are deleted; telly is replaced by a server-owned test comm.
7. **Server deps.edn drops isaac-agent** at runtime (test aliases may keep
   it). Stale sibling pins are a release-step bump or a CI check.
8. **End cap: rename isaac-server → isaac-http** (repo, module id
   `:isaac.http`, berth ids `:isaac.http/route` etc., registry, zanebot
   config, every contributor manifest). Clean cutover, no aliases.

## Legs (children, in order; 3 and 6a/6b can run alongside 1–2)

See child beans. Foundation first: the berth must exist before the agent can
contribute to it. The rename is last.

## Not in scope

Renaming `isaac server` (the command keeps its name; "server" means the
process). The scheduler stays in foundation. Fresh-box "install every
registry module" is an ops note, not code.
