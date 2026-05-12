---
# isaac-6olq
title: "Formalize :tools config tree with programmatic schema registration"
status: completed
type: feature
priority: normal
created_at: 2026-05-08T19:27:13Z
updated_at: 2026-05-08T23:13:49Z
---

## Description

Today the config tree models comms as a fully configurable extension type (:comms slots + per-slot config + manifest-declared schema), but tools have no equivalent. The :tools <name> key is *informally* used by web_search, which reads :api-key and :provider directly from config without any schema registration or validation.

This bead formalizes :tools as a first-class config surface. Built-ins register their schemas programmatically (no manifest, since they ship in core); the same pipeline accepts module-declared schemas later when a module-supplied tool ships (Meep, isaac-p7tq).

The work also surfaces a recursive-extension pattern: tools can expose their *own* extension points. web_search has a :provider sub-extension; each registered provider (brave today) brings its own config schema. The validation pipeline composes the tool's schema with the active provider's schema.

## Spec

The contract is fully specified by features/tools/web_search_config.feature (8 @wip scenarios) plus the @wip-marked existing scenario in features/tools/web_search.feature.

## Tasks

The worker tackles these in order within this single bead. Each is independently shippable as its own commit; the bead closes when all 8 + 1 scenarios pass and acceptance criteria pass.

### 1. Programmatic schema registration API

Add an isaac.config.schema (or similar) namespace exposing register-schema! that takes a kind (:tool, :slash-command, etc.), a name, and a c3kit schema. Built-in tools call this from their own -isaac-init (or the equivalent registration entry); module-declared schemas hook the same API at activation.

### 2. web_search/-isaac-init registers tool + schema

Today web_search has no -isaac-init. Add one that registers (a) the tool spec with tool-registry, and (b) the config schema for :tools :web_search via the new schema-registration API. Emit `:config/schema-registered` log event with `:tool web_search`.

### 3. :tools key in formal config schema

Add :tools to the top-level config schema as `{:type :map :key-spec {:type :keyword} :value-spec {:type :map}}`. Per-tool schemas merge in dynamically as tools register.

### 4. Schema-validation merging

When tool schemas register, the merged schema validates user config under :tools <tool-name> against the registered schema. Unknown keys produce warnings (matching the comm-side schema_composition.feature precedent); type mismatches and missing required fields produce errors.

### 5. Provider sub-extension for web_search

web_search's :provider field is validated against a registered set of providers, not a hardcoded enum. brave registers itself as a provider; its schema declares :api-key as required. Each provider's schema composes with web_search's at validation time.

### 6. Test step support

Add the two new step defs in spec/isaac/features/steps/:
- `Given the {provider:string} provider is registered for {extension:string}` (no schema variant)
- `Given a {provider:string} provider is registered for {extension:string} with schema:` (EDN docstring variant)

Both call the registration API directly. The schema variant parses EDN as data and passes through; c3kit/apron resolves :validations [:required] et al natively.

### 7. Update existing scenario

features/tools/web_search.feature scenario 4 (lines 51-56) is currently @wip. Once :api-key is schema-required at config-load (task 3+4+5), the runtime-error path is unreachable. Either delete the scenario or rewrite it to assert config-load failure with the appropriate error. Worker's call.

### 8. Remove @wip from all 8 web_search_config scenarios + verify

Each scenario's @wip comes off only when that specific scenario passes. Final verification:
```
bb features features/tools/web_search_config.feature
bb features features/tools/web_search.feature
```

## Out of scope

- :slash-commands config tree (separate bead — requires the slash-command extension point to exist first; that bead also doesn't yet exist).
- Module-declared tool config (Meep, isaac-p7tq, deferred). The mechanism this bead builds will be reused by Meep when undeferred; Meep's bead writes its own AT.
- Per-comm schema work (already implemented for :comm extension; this bead doesn't touch it).
- Refactoring how :crew <name> :tools :allow [...] (the per-crew permission allow-list) works. Different concern, different path.

## Acceptance Criteria

bb features features/tools/web_search_config.feature passes; bb features features/tools/web_search.feature passes; bb spec green; no @wip tags remain in either file (or scenario 4 of web_search.feature is rewritten/removed); :tools key appears in the formal config schema; web_search has a -isaac-init that registers both the tool spec and the config schema; :config/schema-registered log event fires with :tool web_search at startup; the two new test step defs exist and are invokable from features.

