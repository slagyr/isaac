---
# isaac-7nd
title: "Dev-mode hot reload for server"
status: completed
type: feature
priority: normal
created_at: 2026-04-10T21:54:52Z
updated_at: 2026-04-10T22:17:35Z
---

## Description

Hot reload source code changes in the server during development. Uses c3kit.apron.refresh (2.5.1+ for babashka compat) to scan file mtimes on every request and reload only namespaces whose files changed.

## Config-driven
Dev mode determined by the `:dev` config key:
- `{:dev true}` in the config file
- `{:dev "${ISAAC_DEV}"}` to pull from env
- `--dev` CLI flag overrides config at startup

Code reads from config, never directly from env vars or CLI.

## Changes
- deps.edn: bump c3kit-apron to 2.5.1, add c3kit-wire (same version)
- src/isaac/cli/server.clj: add --dev flag, merge into config
- src/isaac/server/app.clj: when dev, call refresh/init ["isaac"], wrap root handler with refresh-handler, emit :server/dev-mode-enabled log
- src/isaac/server/routes.clj: convert to lazy-routes so handler symbols are resolved at request time
- Add a :debug log :server/dev-reload-scan emitted per request when refresh-handler runs

## Feature file
features/server/dev-reload.feature (3 @wip scenarios)

## Blocked on
c3kit-apron 2.5.1 published to Clojars

## Acceptance
Remove @wip from all 3 scenarios and verify each passes:
- bb features features/server/dev-reload.feature:11
- bb features features/server/dev-reload.feature:22
- bb features features/server/dev-reload.feature:34

Manual verification: ISAAC_DEV=true bb isaac server, edit a handler file, next request picks up the change.
Full suite: bb features and bb spec pass.

