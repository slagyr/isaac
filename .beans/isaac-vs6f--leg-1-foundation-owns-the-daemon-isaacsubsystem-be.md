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
    - unverified
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

## Implementation handoff

Foundation branch: `bean/isaac-vs6f` @ `0315cc6984610599e99ad72c461e9ed2e60ffe45` (base `origin/main@80c6c1e3abbdb0c1892c70c67164bb8e05f75a8d`).

Server branch: `bean/isaac-vs6f` @ `515574f` (base `origin/main@7f17654a8ff6f9bf15725c047d3b5a88248c197d`). Server pins Foundation `0315cc6984610599e99ad72c461e9ed2e60ffe45` in `bb.edn` and `deps.edn`.

Implemented Foundation-owned `:isaac/component` lifecycle, topological start/reverse stop, supervision, process runner, `server`/`service` commands, service-manager migration, runtime trampoline, scheduler ownership, and lifecycle logging. Server now contributes HTTP as `:http` with `BoundPort`, uses Foundation runner hooks while retaining server-specific setup/teardown, and no longer owns generic service lifecycle or optional Hail start/stop-by-symbol behavior. The previous `services.feature` `@wip` covered the old server-owned service berth; those scenarios moved to Foundation's component feature and the new HTTP component scenario is active without `@wip`.

Verification completed after rebasing both branches on current `origin/main`:

- Foundation `bb features features/component/ features/cli/service.feature features/cli/service_linux.feature`: 31 examples, 0 failures, 122 assertions.
- Foundation `bb spec`: 1018 examples, 0 failures, 1840 assertions.
- Foundation `bb ci`: config bypass lint OK; 1018 specs / 1840 assertions and 181 features / 483 assertions, all green.
- Server `bb features features/server/services.feature features/server/lifecycle.feature`: 3 examples, 0 failures, 5 assertions.
- Server `bb features features/server/dev-reload.feature`: 3 examples, 0 failures, 3 assertions.
- Server `bb spec`: 136 examples, 0 failures, 281 assertions.
- Server `bb ci`: config bypass lint OK; 136 specs / 281 assertions and 46 features / 96 assertions, all green.
- `git diff --check origin/main...HEAD`: clean in both repositories.
- Relevant acceptance feature files contain no `@wip`; one-time search confirms no optional Hail service-by-symbol startup/shutdown path remains in `isaac.server.app`.

Ready for verification and landing. Foundation must land/release before the server branch because the server pin references the Foundation implementation commit.
