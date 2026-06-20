---
# isaac-olj5
title: 'Server boot observability: log every berth registration (:berth/registered)'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-20T20:32:41Z
updated_at: 2026-06-20T20:45:25Z
---

Follow-on to y2bc (module/phase boot logging). y2bc logs :module/loaded/:module/activated, :server/boot-phase, and :server/boot-summary, but NOT individual HTTP route registration. So a 404 (e.g. a hooks webhook call) is undiagnosable from the log — you cannot tell whether the route was ever registered.

Motivation: on zanebot a hook call returned 404; the log gives no way to confirm whether the hooks module contributed its route at boot.

Add: emit a per-route log event as each route is wired up at boot — method + path (+ contributing module/source if cheaply available) — plus a routes summary count. Natural choke point is isaac.server.routes/register-route-entry! (the :isaac.server/route berth factory) and/or register-route!, alongside built-in-routes. Place the events within the existing boot phase sequence so the route list is legible in order.

Goal: 'was /hooks/* registered? by which module? in what order?' answerable directly from the boot log; a 404 becomes self-diagnosing.

Scenarios pending Micah review (do not generate feature file yet).

## Updated scope (2026-06-20 — approach approved: uniform, 1 bean)

Supersedes the routes-only framing above. ALL registrations flow through one
choke point — `isaac.module.loader/process-manifest-berths!` (+ the per-entry
factory path `register-builtin-berth-entry!`) — so a single uniform log event
covers every berth kind in one foundation change:

| Berth | Repo | What registers |
|---|---|---|
| `:isaac/cli` | foundation | CLI commands |
| `:isaac.config/schema` `/check` | foundation | config schema/checks |
| `:isaac.server/route` | server | HTTP routes |
| `:isaac.server/comm` | server | comm impls |
| `:isaac.server/service` | server | services |
| `:isaac.agent/tools` | agent | LLM tools |
| `:isaac.agent/slash-commands` | agent | slash commands |
| `:isaac.hooks/hook` | hooks | hooks |

Today this is half-done and inconsistent: slash-commands already log
`:slash/registered` (`isaac.slash.registry:26`) but routes (and most others) log
nothing. This bean makes it uniform.

### Design
- Emit `:berth/registered {:berth <berth-id> :entry <entry-id> :module <module-id>}`
  for every entry installed at the choke point, in dependency/registration order.
- Emit a `:berth/registration-summary` with a per-berth count at end of the
  registration phase (sits within the y2bc boot-phase sequence).
- Normalize the existing `:slash/registered` onto the uniform event (drop the
  one-off) so there is a single greppable signal. (Chosen "uniform, 1 bean" —
  NOT "keep typed too".)

### Scenarios (approved shape — generate feature under isaac-foundation)
```gherkin
Scenario: Each berth entry is logged with berth, entry id, and module
  Given a module "demo" contributing a :isaac.server/route entry :ping
  When the server boots
  Then the log contains :berth/registered {:berth :isaac.server/route :entry :ping :module :demo}

Scenario: Registrations across berth kinds all appear at boot
  Then the log contains :berth/registered for berth :isaac/cli
  And  the log contains :berth/registered for berth :isaac.server/route
  And  the log contains :berth/registered for berth :isaac.agent/slash-commands

Scenario: Boot emits a per-berth registration summary
  Then the log contains :berth/registration-summary with a count per berth
```

### Acceptance
- Uniform `:berth/registered` emitted for every berth entry at boot (routes,
  cli, tools, slash, comms, services, config schema/check, hooks).
- `:berth/registration-summary` per-berth counts present.
- Existing `:slash/registered` folded into the uniform event.
- A missing route/command (e.g. the hooks 404) is diagnosable from the boot log.

## Worker handoff (2026-06-20, work-3)

Foundation `455e0db`: `process-manifest-berths!` and `register-builtin-berth-entry!`
emit `:berth/registered {:berth :entry :module}` per factory invocation;
`:berth/registration-summary {:counts ...}` closes the pass.

Agent `304d8e5`: removed `:slash/registered` from `slash.registry` (uniform event only);
`slash_extension.feature` uses manifest-berths-processed step. Foundation pin `455e0db`.

Server `5af5022`: foundation pin bumped to `455e0db`.

Proof:
- `isaac-foundation`: `bb spec` → `760/0`; `bb features features/module/berth_registration.feature` → `3/3`
- `isaac-agent`: `bb spec` → green; `clojure -M:dev-local:features features/module/slash_extension.feature` → `3/3`
