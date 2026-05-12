---
# isaac-3rez
title: "Config hot-reload on file change"
status: completed
type: feature
priority: normal
created_at: 2026-04-23T16:17:43Z
updated_at: 2026-04-23T19:53:06Z
---

## Description

Server watches its config directory and reloads the in-memory cfg on any change. Reload is atomic: parse or validation failure rolls back and the previous cfg is preserved. Writes outside config/ don't fire.

Spec: features/config/hot_reload.feature

Design contract (pin in impl, asserted by spec):
- :config/reloaded INFO event on success, with :path (relative to config root)
- :config/reload-failed ERROR event on failure, with :path, :reason (:parse | :validation), :error (string)
- Validation errors formatted as '<dot.path> <validator message>' (e.g. 'models.grover.model must be present'); multiple errors joined by newline
- Parse errors: :error carries the raw exception message (regex-asserted in tests)
- Watcher scoped to config/** only; other writes ignored
- Reload is all-or-nothing per change-set

Out of scope for this bead (per user direction, will be deferred follow-ups):
- Rebinding :server.port / :server.host on reload — cfg atom updates, live socket does not. Documented, not enforced in code.
- :gateway.auth.* propagation to already-open WebSocket connections
- :cron scheduler reconfig on :cron config change

Blocked by: isaac-p2ay (in-memory WatchService test double).

Acceptance:
1. Remove @wip from every scenario in features/config/hot_reload.feature
2. bb features features/config/hot_reload.feature passes
3. bb features and bb spec pass

## Notes

Completed with bb spec green and features/config/hot_reload.feature green. Full bb features still has unrelated failures outside this bead's scope; currently Config Composition malformed EDN expectation and ACP Tool Calls content shape. Tracked under isaac-f88c.

