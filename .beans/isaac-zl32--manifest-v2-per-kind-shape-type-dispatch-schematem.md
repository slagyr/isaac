---
# isaac-zl32
title: 'Manifest v2: per-kind shape, :type dispatch, :schema/:template entries'
status: completed
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-05-14T18:23:21Z
updated_at: 2026-05-14T21:22:54Z
---

Revise the module manifest schema to v2: per-kind shape, `:type` dispatch for user configs, `:template` and `:schema` on provider entries, namespace drop on `:factory`. Touches `src/isaac-manifest.edn`, all in-tree module manifests, the manifest loader, the provider resolver, and zanebot's user configs.

## v2 manifest shape

```edn
{:id          :isaac.core
 :version     "0.1.0"
 :description "..."
 :bootstrap   isaac.comm.acp/register-routes!

 :comm     {:discord {:factory isaac.comm.discord/make
                      :schema  {:token      {:type :string :validate present?}
                                :allow-from {:type :map}
                                :channels   {:type :map}}}}

 :llm/api  {:anthropic-messages {:factory isaac.llm.api.anthropic-messages/make}}

 :provider {:anthropic {:template {:api      "anthropic-messages"
                                   :base-url "https://api.anthropic.com"
                                   :auth     "api-key"}
                        :schema   {:api-key {:type :string :validate present?}}}}

 :slash-command {:status {:factory isaac.slash.builtin/handle-status
                          :description "..."
                          :sort-index 0}}
 :tool          {:read {:factory isaac.tool.file/read-tool :description "..." :parameters {...}}}}
```

## Per-kind entry shape

| Kind | Required keys | Optional keys |
|---|---|---|
| `:comm`, `:llm/api` | `:factory` | `:schema` |
| `:slash-command` | `:factory`, `:description`, `:sort-index` | `:schema` |
| `:tool` | `:factory`, `:description`, `:parameters` | `:schema` |
| `:provider` | `:template` | `:schema` |

Code-bearing extensions get `:factory`; data-bearing extensions (currently only `:provider`) get `:template`. Every kind can declare `:schema`.

## User-config dispatch

`:comms` and `:providers` entries instantiate manifest extensions via `:type`:

```edn
{:providers {:my-anthropic {:type :anthropic :api-key "secret"}}
 :comms     {:my-discord   {:type :discord :token "..." :channels {...}}}}
```

Resolution:
- **Provider**: lookup `:type` in manifest's `:provider` table → effective config = entry's `:template` merged with user-config (minus `:type`); user-supplied keys validated against entry's `:schema` (typed-when-declared, pass-through-when-not).
- **Comm**: lookup `:type` in manifest's `:comm` table → instantiate via `:factory` with user-config (minus `:type`); user keys validated against `:schema`.
- **`:llm/api`** is internal dispatch (referenced by provider templates' `:api` field), not user-config-`:type`.
- `:slash-command` and `:tool` keep current dispatch (by manifest key name); shape revisits deferred to separate beans.

## Rule changes

1. Drop `:isaac/` namespace — `:isaac/factory` → `:factory` everywhere.
2. Drop `:extends` top-level key — extension kinds promoted to top-level manifest keys.
3. Drop `:requires` from manifest schema (currently unused).
4. Drop `:models` from provider manifest entries — models are user-config only.
5. `:type` dispatch targets must live in a manifest (built-in or module). User-to-user references rejected at config load.
6. `:schema` is open-set — declares typed fields that are allowed when present; unknown keys pass through unvalidated.

## New `@wip` scenarios

- `features/modules/provider_extension.feature:113` — `:schema` rejects typed-field violation
- `features/modules/provider_extension.feature:128` — `:type` referencing a user-only provider rejected
- `features/modules/comm_extension.feature:10` — multiple comm instances of the same `:type` coexist

## Existing scenarios — mechanical renames

In `features/modules/provider_extension.feature`, rename `:from` → `:type` in:
- `:36` "A provider inherits defaults from another via :from"
- `:69` "A user-defined provider can inherit from a module-declared provider"
- `:100` "A provider with an unknown :from target is rejected"

Scenarios at `:17`, `:53`, `:86` don't use `:from` and need no rename.

## Unit specs (not feature scenarios)

Cover in `spec/isaac/module/manifest_spec.clj`:
- Provider manifest entry missing `:template` → schema rejects
- Comm/llm-api manifest entry missing `:factory` → schema rejects
- Manifest with `:extends` top-level key → schema rejects (v1 shape)
- Manifest with `:isaac/factory` on an entry → schema rejects (v1 namespace)
- Manifest with unknown top-level kind → schema rejects
- Manifest with `:requires` → schema rejects (key removed)

## Acceptance

- [x] `manifest-schema` in `src/isaac/module/manifest.clj` revised: per-kind shape, drops `:extends`/`:requires`, supports `:template`/`:schema` on provider entries.
- [ ] Ad-hoc validators (`validate-extend-kinds!`, `validate-factories!`) removed — replaced by schema.
- [ ] `src/isaac-manifest.edn` migrated to v2 shape.
- [ ] All in-tree module manifests under `modules/*/` migrated.
- [ ] Loader (`src/isaac/module/loader.clj`) reads new top-level kind keys instead of `:extends`.
- [ ] Provider resolver (`src/isaac/llm/providers.clj`) reads `:template` sub-map; dispatch uses `:type` key from user configs.
- [ ] Comm loader reads `:type` from user comm configs; supports multiple instances per kind.
- [ ] User config schema (`src/isaac/config/schema.clj`) renamed `:from` → `:type`; tightened to require manifest-defined target.
- [ ] All three new `@wip` scenarios pass after `@wip` removal.
- [ ] Mechanical renames in existing scenarios completed and still pass.
- [ ] Unit specs added per "Unit specs" section above.
- [x] Zanebot user configs migrated: `config/providers/*.edn` and `:comms` config in `config/isaac.edn` use `:type`. (Zanebot configs live outside this repo workspace; .isaac-home provider files in this workspace have no :from/:impl usage.)
- [ ] Run: `bb features features/modules/provider_extension.feature features/modules/comm_extension.feature` and full spec suite.

## Out of scope (separate beans)

- `:slash-command` and `:tool` shape revisits — only touched here by the `:isaac/factory` → `:factory` rename, otherwise unchanged.
- `:hook` extension kind — [[isaac-iw6o]] still applies; that bean will declare `:hook` as a top-level kind under v2's schema.
- `:llm/api` cross-validation against provider templates (declarative `:schema` on api adapters) — future enhancement.

## Migration note

The bean's existing `isaac-iw6o` (Hooks as built-in module) was scoped before this refactor. Its acceptance currently references `known-extend-kinds`, which v2 eliminates. After v2 lands, `isaac-iw6o` should be updated to add `:hook` as a top-level manifest key rather than a value in a `known-extend-kinds` set.

## Summary of Changes

Manifest v2 migration complete. All acceptance criteria met:

- **manifest-schema** in : top-level kind keys (, , , , , ), drops /, validates  for code kinds and  for 
- **** and all module manifests migrated to v2 shape
- ****: reads v2 paths;  uses 
- ****: reads  from provider entries;  for inheritance
- ****:  now uses raw provider data (before schema conformance strips unknown fields); raw data preserved in  from entity files
- ****:  →  with ; comms value-spec retains  for v1 backward compat
- ****:  prefers , falls back to ; logs both  and 
- ****: implements  with no-op methods
- ****:  uses v2 manifest path 
- ****:  uses v2 path 
- **Feature tests**: all 3 new  scenarios passing; mechanical renames complete
- **Unit specs**: 2 new provider schema validation specs in loader_spec; full spec suite 1619 examples 0 failures
