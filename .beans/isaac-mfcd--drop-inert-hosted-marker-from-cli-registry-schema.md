---
# isaac-mfcd
title: Drop inert :hosted marker from CLI registry schema and module manifests
status: draft
type: task
priority: normal
tags:
    - cli
created_at: 2026-09-21T02:17:35Z
updated_at: 2026-09-21T02:17:35Z
parent: isaac-eqkb
---

Split from isaac-dqy9. The `:hosted` *registry* marker is now inert: cli-server no longer consults it (every command embeds; only `:local-only` opts out). It is still declared in isaac-foundation `src/isaac-manifest.edn` + `cli/registry.clj` and in module manifests (isaac-acp and others).

isaac-dqy9 keeps the log key `:cli/command-started … hosted true` (observability that the command ran in-process). This bean is the schema/manifest cleanup: drop `:hosted` from the CLI registry schema and every module that still sets it.

Do **not** recut isaac-dqy9. Do **not** remove the log key.

## Acceptance (draft — scenarios at promotion)

`git grep -n ':hosted' -- src resources` in isaac-foundation and consuming modules is empty except the log event. Manifests no longer declare `:hosted true`.
