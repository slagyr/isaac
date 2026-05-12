---
# isaac-yonq
title: "Promote Isaac's built-in surface to a manifest with :isaac/factory"
status: completed
type: feature
priority: normal
created_at: 2026-05-11T23:21:16Z
updated_at: 2026-05-12T04:51:22Z
---

## Description

Today every Api/Tool/Comm/SlashCommand impl registers itself via
side effects at namespace load (api/register!, tool-registry/register-tool!,
etc.). This means:

- Config can only be validated AFTER all impl namespaces have loaded.
- The set of valid :llm/api / :tool / :comm / :slash-command values
  is scattered across def-init forms in src/.
- Modules already use isaac-manifest.edn declaratively; Isaac's own
  built-ins use code. Asymmetric.

The fix is to promote Isaac's surface to a manifest with a per-extension
factory symbol — same pattern modules use, but applied to core.

## Manifest shape

resources/isaac-manifest.edn:

  {:id        :isaac.core
   :version   "0.1.0"
   :bootstrap isaac.core/init                      ; optional, lazy-resolved var
   :extends {:llm/api {:openai-responses
                       {:isaac/factory isaac.llm.api.openai-responses/make}
                       :openai-completions
                       {:isaac/factory isaac.llm.api.openai-completions/make}
                       ...}
             :tool {:grep {:isaac/factory isaac.tool.grep/run
                           :description "..."}
                    ...}
             :provider {:openai-codex {:api "openai-responses"
                                       :base-url "https://api.openai.com/v1"
                                       :auth "oauth-device"}
                        ...}
             :slash-command {:effort
                             {:isaac/factory isaac.slash.builtin/handle-effort}
                             ...}
             :comm {:cli {:isaac/factory isaac.comm.cli/make}
                    ...}}}

Conventions:
- :isaac/factory carries a SYMBOL to a var, resolved lazily via
  requiring-resolve. Namespaced keyword marks it as manifest metadata.
- :bootstrap is also a symbol to a 0-arg fn, resolved lazily. Optional.
  Modules whose extensions all carry their own :isaac/factory do not
  need :bootstrap.
- :provider extensions carry no factory (providers are pure config
  pointing at an :llm/api).
- :llm/api as the manifest kind name (was :api in the original module
  manifest schema). The bare :api was overloaded — it's also the field
  name inside provider config that REFERENCES an api id. The namespaced
  keyword disambiguates and mirrors the source path
  src/isaac/llm/api/X.clj.

## What goes away

- (api/register! :foo make) calls in src/isaac/llm/api/*.clj namespaces.
- Equivalent register! calls in src/isaac/tool/* and src/isaac/comm/*
  and src/isaac/slash/*.
- The src/isaac/llm/providers.clj catalog (move into the manifest).
- The :entry field on existing module manifests (renamed to :bootstrap,
  semantics narrowed to "optional setup hook").
- The bare :api extension kind on module manifests (renamed to :llm/api).

## What enables

- Config validation reads (Isaac manifest + module manifests) as data
  to know the valid set of :llm/api, :tool, :comm, :slash-command,
  :provider ids. No impl namespaces required.
- isaac server / isaac chat startup can validate config before any
  impl namespace loads.
- Boot becomes data-first; namespace loads happen lazily when the
  user first dispatches a turn using a given api/tool/etc.

## Gherkin updates owed (mechanical, included in this bead)

- features/modules/api_extension.feature L3-4: Feature description
  references :entry + register! from -isaac-init. Update to mention
  :isaac/factory and rename :api to :llm/api in the example.
- features/modules/api_extension.feature scenario 4: "A turn against
  an unregistered api fails with a useful reply" — semantics shift
  to config-load-time rejection. Rewrite to use the existing
  "the config is loaded" + "the config has validation errors
  matching:" steps.
- features/modules/slash_extension.feature L2-4: Same description
  rewrite.
- Module manifest tests under spec/isaac/module/manifest_spec.clj
  updated to assert :isaac/factory resolution, :bootstrap renaming,
  and :llm/api kind acceptance.

## Module migration

Three in-tree modules need manifest updates:
- modules/isaac.api.tin-can/resources/isaac-manifest.edn:
    :extends {:api {:tin-can {}}}
    -> :extends {:llm/api {:tin-can {:isaac/factory isaac.api.tin-can/make}}}
- modules/isaac.providers.kombucha/resources/isaac-manifest.edn:
    :extends remains :provider; no factory needed.
- modules/isaac.slash.echo/resources/isaac-manifest.edn:
    :extends {:slash-command {:echo {:isaac/factory ...}}}

The corresponding src/ namespaces drop their -isaac-init register!
calls.

## Self-consistency unit test

A spec that walks Isaac's manifest, requiring-resolves every
:isaac/factory symbol, and asserts non-nil. Catches typos and
manifest/code drift at CI time without paying the runtime cost
of eager namespace loads in production.

## Conditional factories: out of scope

If a module ever needs to choose a factory based on runtime
conditions, the :bootstrap hook is the escape hatch — that namespace
can call (api/register! ...) directly with arbitrary logic. The
manifest's static :isaac/factory covers every case we have today;
first-class conditional support is YAGNI.

## Acceptance Criteria

- resources/isaac-manifest.edn exists and declares all built-in
  :llm/api, :tool, :comm, :slash-command, and :provider entries.
- No (api/register! ...) or equivalent register! calls remain in
  src/isaac/llm/api/*, src/isaac/tool/*, src/isaac/comm/*,
  src/isaac/slash/*.
- src/isaac/llm/providers.clj's catalog atom is gone (or limited to
  pulling from the manifest).
- spec/isaac/manifest_self_consistency_spec.clj exists and passes:
  every declared :isaac/factory and :bootstrap symbol resolves.
- All three existing modules migrated to the new manifest shape
  (:llm/api kind, :isaac/factory per extension, :bootstrap rename);
  their src/ namespaces drop -isaac-init register! calls.
- features/modules/api_extension.feature and slash_extension.feature
  descriptions updated. Scenario 4 of api_extension rewritten to
  use config-load-time validation steps.
- bb features green; bb spec green; bb ci green.
- No regressions in module-shipped tin-can / kombucha / echo
  scenarios.

## Design

## Affected source files

- NEW: resources/isaac-manifest.edn
- src/isaac/module/manifest.clj
    Update schema:
      :extends value-spec gains :isaac/factory key (symbol type, optional
        per extension kind — required for :llm/api/:tool/:comm/
        :slash-command, absent for :provider).
      Rename :entry to :bootstrap (symbol type, optional).
      :known-extend-kinds becomes #{:llm/api :comm :provider
        :slash-command :tool} (was #{:api ...}).
    Keep validate-extend-kinds! and validate-entry! (renamed to
    validate-bootstrap!).
- src/isaac/module/loader.clj
    Read Isaac's own manifest from resources at startup, alongside
    module manifests. Lazily requiring-resolve :isaac/factory symbols
    on first use.
- src/isaac/llm/api/openai_responses.clj, openai_completions.clj,
  anthropic_messages.clj, ollama.clj, claude_sdk.clj, grover.clj
    Drop the bottom-of-file (api/register! :foo make) calls. Move
    the bindings into resources/isaac-manifest.edn under :llm/api.
- src/isaac/tool/*, src/isaac/slash/*, src/isaac/comm/*
    Same drop of register! calls; corresponding manifest entries added
    under :tool, :slash-command, :comm.
- src/isaac/llm/providers.clj
    The catalog atom and core defaults move into the manifest's
    :extends :provider map. Keep the runtime resolve-provider fn.
- modules/isaac.api.tin-can/resources/isaac-manifest.edn,
  modules/isaac.providers.kombucha/resources/isaac-manifest.edn,
  modules/isaac.slash.echo/resources/isaac-manifest.edn
    Migrate :entry -> :bootstrap (where present). Migrate :api -> :llm/api
    (where present). Add :isaac/factory to each extension entry. Drop
    -isaac-init register! calls from these modules' src/ ns bodies.
- spec/isaac/module/manifest_spec.clj
    Update to cover :isaac/factory, :bootstrap rename, :llm/api kind,
    lazy resolve.
- features/modules/api_extension.feature, slash_extension.feature
    Description rewrites + scenario 4 rewrite per the bead description.

## Self-consistency test

spec/isaac/manifest_self_consistency_spec.clj (new):
- Read resources/isaac-manifest.edn.
- For every :extends kind, for every extension, if :isaac/factory
  present: requiring-resolve the symbol, assert non-nil.
- If :bootstrap present at top level: same check.
- Same checks against every modules/*/resources/isaac-manifest.edn.

## Implementation order

Hard rename — no deprecated aliases. The manifest schema accepts
ONLY the new keys (:llm/api, :bootstrap, :isaac/factory). All
in-tree module manifests migrate in the same commit. There are no
external modules to worry about.

1. Update module/manifest.clj schema to the new shape: :llm/api as
   the kind keyword, :bootstrap (replacing :entry), :isaac/factory
   per extension. :known-extend-kinds updated. Reject bare :api and
   :entry outright.
2. Write resources/isaac-manifest.edn with the full Isaac surface.
3. Update loader to consult the manifest first; lazily resolve
   factories.
4. Migrate each src/ namespace to drop its register! call (verify
   manifest entry exists for that capability).
5. Migrate three existing modules' manifests in the same commit
   that ships the schema change.
6. Run self-consistency spec; iterate until green.
7. Update the two gherkin feature files.

## Notes

Verification failed: bb ci is green, but the implementation does not meet the manifest-migration acceptance criteria on the current checkout. resources/isaac-manifest.edn is missing; spec/isaac/manifest_self_consistency_spec.clj is missing; register! calls still remain in src/isaac/llm/api/*, src/isaac/tool/*, src/isaac/comm/*, and src/isaac/slash/*; and module src namespaces still contain -isaac-init/register! code (for example modules/isaac.api.tin-can and modules/isaac.slash.echo).
Re-verified current checkout after sync. Core manifest/factory migration appears present and consistent. Current repo-wide failures are outside isaac-yonq scope and tracked separately:
- isaac-l2u1: turn usage spec references missing clear-caches var
- isaac-zf1g: reasoning effort feature expectations no longer match requests
- isaac-2mv2: context compaction feature fails after head overflow scenario

