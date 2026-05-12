---
# isaac-pcuk
title: "Provider as a first-class extension (separate from Api), with :from inheritance"
status: completed
type: feature
priority: normal
created_at: 2026-05-08T22:42:16Z
updated_at: 2026-05-08T23:17:04Z
---

## Description

Today the Provider concept is conflated with Api in the codebase: `isaac.llm.providers` is a hardcoded `def` catalog, and `isaac.llm.api/-registry` is named "providers" in its docs but actually holds Api wire-format adapters. Adding a new upstream service (xAI, Mistral, Together, Groq, a corp gateway) requires editing core.

This bead separates the two concepts and makes Provider a first-class extension:

- **Api** = wire-format adapter (anthropic-messages, openai-completions, ...). Code. Registered via `isaac.llm.api/register!` from `-isaac-init`. Six built-ins ship today.
- **Provider** = configuration data pointing at one Api with base-url, auth mode, model list. Three contribution paths converge into one registry:
  - Built-in providers (anthropic, openai-codex, grok, ...) registered at startup
  - Module-declared providers (manifest-only, no Clojure code)
  - User-declared providers (inline in isaac.edn under :providers)

xAI is a Provider that uses the openai-completions Api. Anthropic-via-corp-gateway is a Provider that inherits from `:anthropic` with an overridden base-url.

## Spec

The contract is fully specified by features/modules/provider_extension.feature (6 @wip scenarios).

## Tasks

### 1. Convert isaac.llm.providers from def to registry

Today: `(def ^:private catalog {...})`. After: an atom-backed registry with `register!`, `lookup`, `all-providers`, etc. Built-ins register at startup.

### 2. :provider manifest extension kind

Add `:provider` to the recognized set in isaac.module.manifest. The value at `:extends {:provider {<name> <entry>}}` is a complete provider entry (`:api`, `:base-url`, `:auth`, `:models`).

### 3. Manifest :entry becomes optional

Today isaac.module.manifest/manifest-schema requires `:entry`. Provider-only modules have no Clojure code, so `:entry` becomes optional. Module activation checks `:entry`: if present, require the namespace and call `-isaac-init`; if absent, just process the manifest's `:extends` block.

### 4. Add :providers top-level config key

User config can declare providers inline under `:providers {<name> <entry>}`. Schema validation merges in dynamically — uses the schema-registration pipeline isaac-6olq builds.

A user-declared provider is structurally the same as a module-declared provider (same fields). The only difference is the source of truth. Both flow into the same registry at config-load.

### 5. :from inheritance

A provider entry may have `:from <other-provider-name>` instead of (or alongside) explicit fields. Resolution merges the source provider's fields with the deriving provider's fields, with deriving wins. `:from` resolves against the full registry: built-in, module-declared, or other user-declared (i.e. a user provider can inherit from another user provider, though this is unusual).

### 6. Validation: unknown :api fails at config-load

A provider's `:api` keyword must match a registered api. Unknown values produce validation errors. Catches typos before they hit runtime.

### 7. Validation: unknown :from fails at config-load

A provider's `:from` keyword must resolve to a registered provider. Catches typos and stale references.

### 8. Fixture module: modules/isaac.providers.kombucha/

Single file: `resources/isaac-manifest.edn`. No `src/`, no `:entry`, no Clojure code. Manifest:

```edn
{:id      :isaac.providers.kombucha
 :version "0.1.0"
 :extends {:provider {:kombucha {:api      "openai-completions"
                                 :base-url "https://api.kombucha.test"
                                 :auth     "api-key"
                                 :models   ["kombucha-large" "kombucha-small"]}}}}
```

This fixture is used by scenarios 3 and 4 in the feature file.

### 9. Test step support

All step phrases in the feature file already exist (`the user sends ... via memory comm`, `the last outbound HTTP request matches:`, `the config is loaded`, `the config has validation errors matching:`). No new step defs.

If `the user sends ... via memory comm` still says "memory channel" in the step source at the time of this work, the rename happens here OR in isaac-up9y, whichever lands first.

### 10. Remove @wip and verify

```
bb features features/modules/provider_extension.feature
bb features features/modules/activation.feature
bb features features/modules/coordinates.feature
bb features features/modules/discovery.feature
bb features features/modules/schema_composition.feature
```

The non-provider module features pass to ensure the manifest changes (optional :entry, new :provider kind) don't regress comm-extension behavior.

## Out of scope

- Api extension point for code-shipping modules (separate bead — when someone wants to add a wire format outside the 6 built-ins).
- Migrating built-in Api impls (`isaac.llm.api.<name>`) into module form. They stay in core.
- Cyclic `:from` detection. Edge case, deferrable.
- Forward-reference `:from` semantics within a single config. Worker decides; document the chosen contract.
- Provider activation logging for module-only modules. Worker decides whether to fire `:module/activated` for data-only modules.

## Depends on

- **isaac-6olq** — schema-registration pipeline used by :providers config validation.

## Acceptance Criteria

bb features features/modules/provider_extension.feature passes (all 6 scenarios green, no @wip remaining); bb features features/modules/{activation,coordinates,discovery,schema_composition}.feature still pass (comm-extension regression); bb spec green; isaac.llm.providers exposes a register!/lookup API instead of a hardcoded def; isaac.module.manifest accepts :provider as a recognized :extends kind; isaac.module.manifest's :entry field is optional; modules/isaac.providers.kombucha/ exists as manifest-only (no src/, no :entry); :providers and :from validation rejects unknown :api keywords and unknown :from targets at config-load.

