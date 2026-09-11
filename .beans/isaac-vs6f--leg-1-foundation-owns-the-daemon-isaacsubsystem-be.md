---
# isaac-vs6f
title: 'Leg 1 — foundation owns the daemon: :isaac/component berth + supervisor, process runner behind ''isaac server'', ''isaac service'' OS manager'
status: in-progress
type: feature
priority: high
tags:
    - foundation
    - server
    - component
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-11T06:00:42Z
parent: isaac-3q4m
---

Parent: isaac-3q4m (decisions 1–3).

Repo: **isaac-foundation** (receives), **isaac-server** (sheds).

Move, file for file where possible: isaac-server `src/isaac/service/{cli,manager,launch,macos,linux,protocol,registry,factory,runtime,supervisor}.clj` → foundation. The in-process half becomes `isaac.component.*` behind a foundation-declared berth `:isaac/component` keeping the existing entry shape `{<id> {:namespace <sym>}}` — the namespace implements the Service protocol (start/stop) and optionally Supervised (alive?) — with start in module topological order and stop in reverse (rename of `:isaac.server/service`; clean cutover; log events `:service/started|stopped` become `:component/started|stopped`). The OS half keeps the `isaac service` command (launchd + systemd; the lqbc Linux work moves with it). The process runner (today isaac-server `app.clj` start!/stop!: load config → activate modules → start components in topological order → wait → stop in reverse) moves behind foundation's `isaac server` command; the HTTP listener becomes a component entry contributed by isaac-server's manifest. Hail's optional-service-by-symbol special case disappears (it is an entry).

Note: isaac-server `features/server/services.feature` (the three berth scenarios) is still tagged @wip although Discord runs through the berth today — find out why before moving it.

Scenarios: the three services.feature scenarios move to foundation under the new berth id and event names; the `isaac service` features (service.feature, service_linux.feature) move unchanged; NEW: the HTTP listener starts as a component of isaac-server; NEW: foundation's `isaac server` boots with zero components and says so.

Blocks legs 2 and 4 (the berth must exist first).

## Acceptance

- isaac-server d49972f: `features/server/services.feature` — NEW scenario "the HTTP listener is a component of isaac-server" (@wip). The three existing scenarios in that file move to foundation `features/component/` under `:isaac/component` with `:component/started|stopped`; explain the file's @wip tag before moving it.
- foundation: `features/cli/service.feature` + `service_linux.feature` moved from isaac-server unchanged; spec: the runner boots with zero components and logs `:runner/started :components 0`, then stops cleanly.
- Hail's optional-service-by-symbol path in isaac-server `app.clj` is gone (one-time check).

```
cd isaac-foundation && bb features features/component/ features/cli/service.feature features/cli/service_linux.feature && bb spec && bb ci
cd isaac-server && bb features features/server/services.feature features/server/lifecycle.feature && bb spec && bb ci
```

All green with @wip removed; bb ci green in both repos. Train: foundation release (brew HEAD), then isaac-server bump pinned to it.
