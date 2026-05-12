---
# isaac-4dpt
title: "Add server.hot-reload config to skip watcher startup in non-reload tests"
status: completed
type: task
priority: normal
created_at: 2026-04-27T17:04:53Z
updated_at: 2026-04-27T17:57:23Z
---

## Description

Server startup under bb always starts the config watcher, and the bb fswatcher path sleeps 1 second to let FSEvents settle. Most server scenarios do not exercise config reload, so they should be able to disable watcher startup explicitly via config. Add a server.hot-reload flag (default true), use it in app/start!, and update non-reload server features to turn it off.

## Acceptance Criteria

1. Config schema and server-config resolution support server.hot-reload with default true. 2. app/start! does not create/start the config change source when hot-reload is false. 3. Non-reload server features disable hot-reload and become measurably faster. 4. Hot-reload features still exercise reload behavior. 5. bb features and bb spec pass.

## Notes

Follow-up from server timing analysis: the ~1s cluster is bb fswatcher startup sleep in change_source_bb.clj.

