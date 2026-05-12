---
# isaac-juu8
title: "Module-shipped Api wire formats: :api manifest kind + activation hook"
status: completed
type: feature
priority: low
created_at: 2026-05-08T22:55:24Z
updated_at: 2026-05-09T19:57:30Z
---

## Description

The Api registry mechanism is fully working in core (`isaac.llm.api/register!` invoked from each built-in's `-isaac-init`), but it's not exposed as a manifest-declared extension point. Modules cannot today ship a new wire-format adapter (e.g., a Cohere impl, a corp's bespoke gateway, a local-only experimental wire format) without modifying core.

This bead closes that gap. After this work, a module can ship a new Api by:

1. Declaring `:extends {:api {<name> {}}}` in its manifest with an `:entry` namespace.
2. Implementing the `Api` protocol in that namespace.
3. Calling `(api/register! :<name> make)` from `-isaac-init`.

Most third-party additions are Providers (data, no code, see isaac-pcuk). Api-shipping modules are rarer — only needed when a new wire format is introduced. But the pathway must exist for the rare case.

## Spec

The contract is fully specified by `features/modules/api_extension.feature` (4 @wip scenarios).

## Tasks

### 1. Recognize :api as a manifest extension kind

Add `:api` to the closed set of recognized `:extends` kinds in `isaac.module.manifest` (alongside :comm, :tool, :provider, :slash-command). Validation accepts module manifests declaring `:extends {:api {<name> <config-schema>}}`.

For now, the inner config-schema can be `{}` — apis don't have config that flows into user `isaac.edn`. The `:provider` extension carries config; the `:api` extension is just code registration.

### 2. Module activation triggers api -isaac-init

When the module loader activates a module that declares `:extends {:api ...}`, it requires the `:entry` namespace and calls `-isaac-init`. This is the same pattern as `:comm` and `:slash-command` — no new mechanism, just include `:api` in the activation triggers.

### 3. Drive layer guards against unregistered api at runtime

In `isaac.drive.dispatch/make-provider` (or wherever a Provider's `:api` keyword resolves to a factory), check `(api/factory-for k)`. If nil, return a structured error response that surfaces through the comm as a user-readable message containing "unknown api: <keyword>". Don't throw.

### 4. New log event :api/registered

Fired by the registry's `register!` (or by callers of it). Carries `{:api "<name>"}`. Convention parallels `:tool/registered` and the upcoming `:slash/registered`.

### 5. Fixture module: modules/isaac.api.tin-can/

Files:
- `resources/isaac-manifest.edn` — declares `:extends {:api {:tin-can {}}}`, `:entry isaac.api.tin-can`
- `src/isaac/api/tin_can.clj` — `defrecord` implementing the Api protocol with canned "tin-can heard: <last user message>" responses; `-isaac-init` registers via `(api/register! :tin-can make)`
- `deps.edn`

The Api impl needs:
- `chat`: returns `{:message {:content (str "tin-can heard: " <last-user-content>)}}`
- `chat-stream`: calls `on-chunk` with the chat result content, returns the chat result
- `followup-messages`: returns `(:messages request)` (or empty — tin-can doesn't iterate)
- `config`: returns the cfg passed to make
- `display-name`: returns the name passed to make

This is a stub for testing the registration pipeline, not a useful Api.

### 6. Test step support

All step phrases in `features/modules/api_extension.feature` already exist (`the user sends ... via memory comm`, `the reply contains`, `the config is loaded`, `the config has validation errors matching:`, `the log has entries matching:`). No new step defs.

### 7. Remove @wip and verify

```
bb features features/modules/api_extension.feature
bb features features/modules/provider_extension.feature
bb features features/modules/activation.feature
```

The provider and activation features pass alongside to ensure the manifest-kind addition doesn't regress comm or provider extension.

## Out of scope

- Migrating built-in Api impls (`isaac.llm.api.<name>`) into module form. They stay in core; modules add to the set rather than replace.
- Per-api config schemas. The current design keeps config at the Provider level (`:providers <name>` carries `:base-url`, `:auth`, etc.); apis themselves have no user config. If that ever changes, a separate bead.
- Protocol changes. The `Api` protocol stays as-is.

## Depends on

- **isaac-pcuk** (Provider as first-class extension) — the validation pipeline that rejects unknown :api keywords lives there. This bead extends the registry it queries.

## Acceptance Criteria

bb features features/modules/api_extension.feature passes (all 4 scenarios green, no @wip remaining); bb features features/modules/provider_extension.feature passes (regression check across the api+provider boundary); bb features features/modules/activation.feature passes (comm-extension regression); bb spec green; isaac.module.manifest accepts :api as a recognized :extends kind; module activation calls -isaac-init for modules declaring :extends {:api ...}; modules/isaac.api.tin-can/ exists with manifest, src, deps and registers a working Api stub; isaac.drive's Api resolution returns a useful error reply when factory-for returns nil at runtime; :api/registered log event fires when an api is registered.

