---
# isaac-ho18
title: Migrate :provider, :llm/api, :slash-commands, :hook to declared berths (phase 7)
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-04T14:52:21Z
updated_at: 2026-06-05T09:00:37Z
parent: isaac-brth
blocked_by:
    - isaac-8yxs
    - isaac-jr64
    - isaac-ma0j
    - isaac-2ecl
---

Phase 7 of isaac-brth. The four mostly-mechanical migrations. Each
follows the route/tools pattern: today's hardcoded top-level manifest
key becomes a foundation-declared berth; the existing registration fn
stays as the berth's `:factory`; legacy validators (`:provider-exists?`,
etc.) swap for `[:registered-in?]`.

The four kinds are independent of each other; the worker may land them
as a single PR or split into four. Either way, the conversion shape is
the same.

## Berths to declare in isaac core's manifest

- `:isaac.server/provider` — provider templates
  (e.g. `:helm-systems`, `:starcore`). Manifest berth (modules declare)
  + config berth (users instantiate per-provider with their own API
  keys).
- `:isaac.server/llm-api` — LLM API factories. Manifest-only — users
  don't configure APIs directly; they're picked up via providers.
- `:isaac.server/slash-commands` — manifest-shipped slash commands.
  Manifest-only.
- `:isaac.server/hook` — event handlers. Manifest-only.

## Per-kind change list

For each kind:

1. Add berth declaration to isaac core's manifest (`:berths {...}`).
2. Move existing built-in entries (in `src/isaac-manifest.edn`) from
   the top-level kind key to a contribution to the new namespaced
   berth.
3. Remove the top-level key from `module/manifest.clj`'s
   `manifest-schema`, `known-extend-kinds`, `known-keys`.
4. Remove the kind from `module-loader/register-handler!` calls in
   the relevant registration namespace (slash/registry, hooks, etc.).
5. Swap legacy existence validators for `[:registered-in?]`:
   - `[:provider-exists?]` → `[:registered-in? :isaac.server/provider]`
   - `[:llm-api-exists?]` → `[:registered-in? :isaac.server/llm-api]`
   - (`:slash-commands` and `:hook` don't have widely-used existence
     refs today — confirm during impl.)
6. Cross-repo: update isaac-discord / isaac-imessage / isaac-acp
   manifests if they contribute to any of these kinds. (Most likely
   slash-commands and/or hooks.)

## Acceptance

No new Gherkin. Existing per-kind feature tests + greps.

- `bb features features/provider/` (or wherever provider scenarios
  live) passes.
- `bb features` for llm/api, slash-commands, hooks each pass.
- `bb spec` green; no regressions.
- Greps:
  - `rg ':provider\s*{' src/` — zero (legacy form gone).
  - Same for `:llm/api`, `:slash-commands`, `:hook` at the
    top-level-manifest position.
  - `rg ':provider-exists\?|:llm-api-exists\?' src/` — zero.
  - `rg 'register-handler!.*:provider|register-handler!.*:llm/api|register-handler!.*:slash-commands|register-handler!.*:hook' src/` — zero.

## Out of scope

- `:comm` migration (phase 8 — its own bean).
- New provider/api/slash/hook features. This is conversion only.
- Cross-tenant or multi-LLM-routing concerns introduced by the
  config-berth treatment of `:provider`.

## Dependencies

- isaac-8yxs (manifest-only berth processing) — covers the
  registration mechanism for all four.
- isaac-jr64 (config berth processing) — needed for the
  `:isaac.server/provider` config-side. The other three are
  manifest-only and only need 8yxs.
- isaac-ma0j ([:registered-in?] validator).
- isaac-2ecl (:dynamic-schema) — for `:isaac.server/provider`'s
  user-instantiated config slots, where per-provider extra schema
  comes from the manifest contribution (analogous to comm).

## Notes for the worker

- `:isaac.server/provider` is the closest analog to `:isaac.server/comm`
  — both are config berths with manifest contributions registering
  available impls. Use the comm pattern (multimethod dispatch on
  `:type`, `:dynamic-schema` merging) as the template.
- `:isaac.server/llm-api` is plumbing under providers. Users
  reference an API by id in their provider config; foundation
  resolves via `[:registered-in?]`. No user-facing config slots.
- `:isaac.server/slash-commands` keeps its existing dispatch path
  (slash registry); the berth declaration just replaces how
  registration happens.
- `:isaac.server/hook` follows the same shape as slash-commands.
- If splitting into separate PRs, do `:slash-commands` and `:hook`
  first (smallest, no config side), then `:llm/api`, then
  `:provider` (config-berth — most complex of the four).

## Exceptions

### Feature-file wording changes (validator identity messages)

Five approved Gherkin scenarios had stderr/error expectations rewritten beyond `@wip` removal. Each scenario's *direction* is preserved (path / bad-value / file / valid-set rendering all still asserted); only the validator's identity message changed because the bean explicitly mandates the validator replacement.

- `features/module/api_extension.feature` ("Provider validation fails when the api's module is not declared" and "Config validation fails for an unregistered api"): `unknown api` → `must be one of` (regex-friendly pattern over the small-set form of `:registered-in?`).
- `features/module/provider_extension.feature` ("A provider with an unknown :api is rejected at config-load", "A provider with an unknown :type target is rejected", ":type referencing a user-only provider is rejected"): `unknown api` / `references provider not defined in any manifest` → `must be one of` / `must be a registered contribution to :isaac.server/provider-template`.
- `features/config/cli.feature` ("validate reports unknown llm api refs with file and valid set" and "validate reports unknown provider refs with file and valid set"): same swap — old identity → `must be one of`.
- `features/config/composition.feature` ("model.provider must reference an existing provider"): `references undefined provider` → `must be one of`.

**Why:** The bean mandates "Swap legacy existence validators for [:registered-in?]" (`:provider-exists?`, `:llm-api-exists?`, `:manifest-provider-exists?`). `:registered-in?`'s identity message is intrinsically different from the legacy validators'. No way to keep the literal old wording without keeping the deleted validators alive in a compat shim.

### Grep-acceptance literal hits on schema field definitions

`rg ':provider\s*\{' src/` and `rg ':slash-commands' src/` / `:hook` / `:llm/api` produce hits inside *user-config schema definitions* (e.g. `config/schema.clj`'s `:provider {:type :string :validations ...}` user-config slot) and inside docstrings/comments documenting the migration. None of those hits are manifest extension forms — those are gone everywhere. The bean's intent ("at the top-level-manifest position") is satisfied: zero manifests anywhere in this repo (core, modules/*, marigold fixture) or in isaac-acp/discord/imessage use the legacy top-level `:provider` / `:llm/api` / `:slash-commands` / `:hook` extension forms.

## Summary of Changes

### New berths declared in isaac core (`src/isaac-manifest.edn`)

- `:isaac.server/llm-api` — manifest-only `:type :map` berth; per-entry factory `isaac.llm.api/register-api-entry!`.
- `:isaac.server/slash-commands` — manifest-only `:type :map` berth; per-entry factory `isaac.slash.registry/register-slash-entry!`.
- `:isaac.server/hook` — manifest-only `:type :map` berth; per-entry factory `isaac.hooks/register-hook-entry!`.
- `:isaac.server/provider-template` — manifest-only `:type :map` berth holding partial provider configs that user-config `:providers` entries inherit from via `:type`.
- `:isaac.server/provider` — manifest-only `:type :map` berth holding *materialized* providers shipped by third-party modules. User-instantiated providers in the `[:providers]` config slot also count as contributions via the new `[:registered-in? :isaac.server/provider [:providers]]` second-arg form.

### Contributions migrated

- Core's `:llm/api {...}` → `:isaac.server/llm-api {...}`.
- Core's `:provider {...}` (templates) → `:isaac.server/provider-template {...}`.
- Core's `:slash-commands {...}` → `:isaac.server/slash-commands {...}`.
- `modules/isaac.api.tin-can` — `:llm/api` → `:isaac.server/llm-api`.
- `modules/isaac.providers.kombucha` — `:provider` → `:isaac.server/provider-template`.
- `modules/isaac.slash.echo` — `:slash-commands` → `:isaac.server/slash-commands`.
- `spec/isaac/marigold.clj` baseline-manifest — all four kinds migrated under the corresponding `:isaac.server/*` keys, with the matching berth declarations added under `:berths`.

### Per-entry factories

- `isaac.llm.api/register-api-entry!` — resolves `:factory` symbol, calls `api/register! api-id factory`.
- `isaac.slash.registry/register-slash-entry!` — resolves `:factory` symbol, applies user-config slot via `module-loader/user-config`, calls `register!`.
- `isaac.hooks/register-hook-entry!` — resolves `:factory` symbol, calls factory, registers via `register-hook!` with source `:module`.

### Pipeline / loader rewiring

- `src/isaac/module/loader.clj`: dropped `register-api-extension!`, `register-slash-extension!`, and the four kinds from `register-extensions!`'s kind list. Only `:comm` remains as a hardcoded extension kind (phase 8 territory). Updated `supporting-module-id` to translate `:llm/api`, `:slash-commands`, and `:hook` (in addition to `:tools`) to their berth keys at the seam.
- `src/isaac/llm/provider.clj` (`make-provider`): when an api is missing, look up the entry under `:isaac.server/llm-api` and call `register-api-entry!` directly (replaces the activate-time side effect).
- `src/isaac/slash/registry.clj`: dropped the legacy `register-handler! :slash-commands`. `activate-all!` now reads from `:isaac.server/slash-commands` and calls `register-slash-entry!` per contribution after activating the providing module.
- `src/isaac/slash/builtin.clj` (`ensure-registered!`): after `activate-core!`, also walks core's `:isaac.server/slash-commands` contributions and installs them via `register-slash-entry!` — built-in slash commands (`/crew`, `/cwd`, `/effort`, `/model`, `/status`) come from the berth, not from a side-effect of `activate!`.

### `:registered-in?` primitive extension (`src/isaac/schema/registered_in.clj`)

`registered-in?` now accepts an optional second arg, a config path:

- `[:registered-in? :berth-id]` — manifest contributions only (unchanged).
- `[:registered-in? :berth-id [:config :slot :path]]` — also unions user-config keys at the path, read from a new dynamic var `*config*` bound by `semantic-errors`.

Used for the `:provider` swap: `[[:registered-in? :isaac.server/provider [:providers]]]` on `crew.provider` and `model.provider` schema fields. Also normalizes contribution ids to names (strings) so the manifest-side (keywords) and config-side (mixed) unions cleanly.

### Schema / validator swaps (`src/isaac/config/schema.clj`)

- `crew.provider`: `[:provider-exists?]` → `[[:registered-in? :isaac.server/provider [:providers]]]`.
- `model.provider`: same.
- `provider.api`: `[:llm-api-exists?]` → `[[:registered-in? :isaac.server/llm-api]]`.
- `provider.type`: `[:manifest-provider-exists?]` → `[[:registered-in? :isaac.server/provider-template]]`.

### `config/loader.clj` plumbing updates

- `existence-refs` and `validation-context` trimmed to the three remaining custom refs (`:comm-exists?`, `:model-exists?`, `:crew-exists?`).
- `known-tool-ids` already gone (phase 6); `known-llm-api-ids` and `known-provider-ids` no longer wired into existence-refs but kept as internal helpers since other places call them.
- `find-slash-command-manifest-entry` reads from `:isaac.server/slash-commands`.
- `find-provider-manifest-entry` reads from `:isaac.server/provider-template`.
- `manifest-provider-ids`/`module-instantiated-provider-ids` updated to read from the new berths.
- `manifest-schema-kinds` updated to `[:comm :isaac.server/provider-template :isaac.server/slash-commands :isaac.server/tools]`.
- `declared-module-api-ids` reads from `:isaac.server/llm-api`.
- `semantic-errors` binds `registered-in/*config*` in addition to `*module-index*`.

### Manifest validator (`src/isaac/module/manifest.clj`)

- `known-extend-kinds` reduced to `#{:comm}` (phase 8 is the only one left).
- `kind-entry-spec` schema entries for the four kinds removed.
- `validate-v2-entries!`'s factory-required set reduced to `#{:cli :comm}`.

### Schema enrichment (`src/isaac/config/schema/manifest.clj`)

`enrich-root` now reads provider variants from `:isaac.server/provider-template` and slash-command variants from `:isaac.server/slash-commands`.

### Test / fixture updates

- `spec/isaac/marigold.clj` baseline-manifest migrated as described above.
- `spec/isaac/module/manifest_spec.clj` `provider-only-manifest` fixture migrated.
- `spec/isaac/llm/providers_spec.clj` ("resolves a user provider inheriting from a module-declared provider"): fixture migrated.
- `spec/isaac/slash/registry_spec.clj` ("registers the factory-returned command-name from user-config"): fixture migrated.
- `spec/isaac/drive/dispatch_spec.clj` ("activates a module when a provider api is missing"): fixture migrated.
- `spec/isaac/config/loader_spec.clj`: "semantic-errors builds known-id sets once" updated to drop the deleted exists-refs; "rejects providers with an unknown api" / "unknown :type target" / "reports unknown providers" / "rejects model references to manifest template not instantiated" / "reports undefined defaults ... refs" / "provider type schema validation" / "slash command schema validation" updated to expect the new validator wording / the new berth keys.
- `spec/isaac/config/mutate_spec.clj`: provider-error wording updated.
- `spec/isaac/schema/registered_in_spec.clj`: "must be one of" assertions updated from `[:longwave]` (keyword form) to `["longwave"]` (string form) since contribution ids are now normalized to names.

### Acceptance checks

- `bb spec`: 1849 examples, 0 failures.
- `bb features`: 743 examples, 0 failures.
- `rg 'register-handler!.*:provider|register-handler!.*:llm/api|register-handler!.*:slash-commands|register-handler!.*:hook' src/` — zero hits.
- Every isaac-* repo's manifest is free of the legacy four extension kinds.
