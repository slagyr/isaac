---
# isaac-up9y
title: "Slash command extension point + :slash-commands config tree"
status: completed
type: feature
priority: normal
created_at: 2026-05-08T19:55:17Z
updated_at: 2026-05-08T20:45:36Z
---

## Description

Today slash command dispatch is a hardcoded `case` in src/isaac/bridge/core.clj:100-119 covering /status, /crew, /model, /cwd. There is no registry, no extension hook, no manifest capability. Adding a new slash command requires editing bridge code directly.

This bead introduces a slash-command extension point modeled after Telly (for comm) and the upcoming Meep (for tool, isaac-p7tq). After this work, modules can ship slash commands via :extends {:slash-command {<name> <config-schema>}} in their manifests, and the example fixture isaac.slash.echo demonstrates the full pipeline including a config-driven name override.

The `:slash-commands` config tree formalization rides along since it has no real-world subject today — without it, the example module's :command-name field has nowhere to live.

## Spec

The contract is fully specified by features/modules/slash_extension.feature (4 @wip scenarios). Each is independently shippable as a commit.

## Tasks

### 1. Slash registry

Add `isaac.slash.registry` mirroring `isaac.tool.registry`'s shape: register!/unregister!/lookup/all-commands. Each entry is `{:name "echo" :description "..." :handler (fn [state-dir session-key input ctx] ...)}`.

Collision behavior: last-wins, log a `:slash/override` warning. Matches the user's intent that modules can intentionally override built-ins.

### 2. Built-in slash dispatch via registry

The four built-ins (status, crew, model, cwd) move from the `case` in bridge.core/handle-slash to register themselves with the registry. handle-slash collapses to `(if-let [cmd (registry/lookup name)] ((:handler cmd) ...) unknown-cmd)`.

Each built-in's spec carries its `:description` so available-commands renders the same labels users see today.

### 3. available-commands enumerates the registry

bridge.status/available-commands replaces its hardcoded vector with `(slash-registry/all-commands)`. Both built-ins and module-supplied commands appear.

### 4. Manifest `:slash-command` kind recognized

Add `:slash-command` to the closed set of recognized :extends kinds in isaac.module.manifest. Schema validation flows through the same pipeline isaac-6olq builds for `:tool`.

### 5. `:slash-commands` top-level config key

Add `:slash-commands` to the formal config schema. Per-command schemas merge in dynamically when modules activate (using the schema-registration mechanism isaac-6olq builds; this bead does NOT reimplement it — it uses it for `:slash-command` kind).

### 6. Example module: modules/isaac.slash.echo/

Files:
- resources/isaac-manifest.edn — `:extends {:slash-command {:echo {:command-name {:type :string :default "echo"}}}}`
- src/isaac/slash/echo.clj — `-isaac-init` reads the configured command-name (defaults to "echo") and registers via `slash-registry/register!`. Handler echoes the args verbatim.
- deps.edn

The default-of-"echo" lets scenarios 1-3 work without explicit config; scenario 4's `:slash-commands {:echo {:command-name "status"}}` exercises the override path.

### 7. New log events

- `:slash/registered` (info) — fires when a slash command registers; carries `{:command "echo"}`.
- `:slash/override` (warn) — fires when a registration replaces an existing command; carries `{:command "status"}`.

### 8. New test step definitions

- `Then the available slash commands include:` — boring wrapper around `slash-registry/all-commands`, asserts table rows present in the result.

### 9. "memory channel" → "memory comm" rename

The existing step phrases in spec/isaac/features/steps/comm.clj use "memory channel" terminology that predates the Comm protocol generalization (isaac-sklm). Rename:
- `the user sends "..." on session "..." via memory channel` → `... via memory comm`
- `the memory channel has events matching:` → `the memory comm has events matching:`

Update any features still using "memory channel". One-time rename, small blast radius.

### 10. Remove @wip and verify

- `bb features features/modules/slash_extension.feature` passes
- `bb features features/chat/toad.feature` still passes (existing /status regression check via ACP)
- `bb spec` green

## Out of scope

- Per-comm extension manifests (Telly already works).
- Per-tool extension manifests (Meep / isaac-p7tq, deferred).
- Provider sub-extension under slash commands (web_search has it; slash commands don't need it).
- Refactoring how the bridge loads/instantiates the registry (use defonce atom, same as tool.registry).

## Depends on

isaac-6olq — provides the schema-registration API and validation pipeline. This bead consumes both for the `:slash-command` kind.

## Acceptance Criteria

bb features features/modules/slash_extension.feature passes (all 4 scenarios green, no @wip remaining); bb features features/chat/toad.feature passes (existing /status regression); bb spec green; isaac.slash.registry exists with register!/unregister!/lookup/all-commands; src/isaac/bridge/core.clj handle-slash has no hardcoded case branches; bridge.status/available-commands enumerates the registry; modules/isaac.slash.echo/ exists with manifest, src, deps; :slash-commands appears in the formal config schema; the 'memory channel' step phrases are renamed to 'memory comm' across spec/isaac/features/steps/ and any features.

