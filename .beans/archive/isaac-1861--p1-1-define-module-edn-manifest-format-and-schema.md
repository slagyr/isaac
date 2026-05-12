---
# isaac-1861
title: "P1.1 Define module.edn manifest format and schema"
status: completed
type: task
priority: normal
created_at: 2026-04-30T22:35:54Z
updated_at: 2026-05-04T18:23:09Z
---

## Description

## v0 placeholder

Treat this bead as the manifest *placeholder*: shape and schema will
grow as later beads (registry contribution, lifecycle, lazy
activation) clarify what modules need to declare. Field set below is
the starting point, not the final word.

Every module (built-in or third-party) ships a module.edn at its
root. Isaac reads it at parse time as pure data — no module code is
loaded.

## Manifest fields (v0)

- :id           — namespaced keyword (e.g. :isaac.comm/discord).
                  Canonical identity. Authoritative over directory
                  name. Collision detection lives in discovery
                  (separate bead).
- :entry        — namespace symbol (e.g. isaac.comm.discord).
                  Required, non-nil. Activation does:
                    (require entry)
                    (when-let [init (ns-resolve entry (quote -init))]
                      (init host))
                  -init is optional. Modules that just need defonce
                  side-effect registrations skip it.
                  Cross-manifest uniqueness check lives in discovery
                  (separate bead).
- :version      — string. Free-form for now. No version-constraint
                  semantics yet.
- :description  — string, optional. Display only.
- :requires     — vector of module ids, optional, default [].
                  Hard semantic dependencies (schema refs, registry
                  contributions, capability deps) that aren't
                  enforced by Clojure's :require chain. At
                  activation, host topo-sorts and activates deps
                  first; cycles fail-fast; missing dep fails-fast.
                  Initial built-ins all have :requires [].
- :extends      — map of registry contributions, shape:

                    {<ext-type-keyword>
                     {<impl-name-keyword> <ext-type-specific-data>}}

                  Each ext-type-keyword names an extension type
                  (e.g. :comm, :provider-api, :tool). The host has
                  one registry per ext-type. The value at
                  <impl-name-keyword> is opaque to the manifest
                  schema; each ext-type defines its own value
                  contract.

                  Example for the :comm extension type, where the
                  value is the bare field map describing fields a
                  slot of this impl has (the ext-type already knows
                  the slot is a :type :map):

                    :extends
                    {:comm
                     {:discord
                      {:token       {:type :string :validations [:present?]}
                       :message-cap {:type :int    :coercions [[:default 2000]]}
                       :allow-from  {:type :map
                                     :schema {:users  {:type :seq :spec {:type :string}}
                                              :guilds {:type :seq :spec {:type :string}}}}
                       :crew        {:type :string}}}}

                  Multi-impl modules group impls under one
                  ext-type:

                    :extends {:comm {:discord {...} :slack {...}}}

                  Multi-ext-type modules:

                    :extends {:comm         {:discord {...}}
                              :provider-api {:openai-compatible nil}}

## Schema fragments use ref-keyed entries (c3kit.apron 2.7.0)

Schemas embedded in :extends values MUST express predicates and
coercers as ref-keyed entries, not inline functions:

- Use :validations / :coercions (plural) — these accept refs.
- Do NOT use :validate / :coerce (singular) — those are
  function-only and won't load from EDN.

Allowed forms inside :validations / :coercions:

- Keyword ref:        [:positive?]
- Factory ref:        [[:> 5]] [[:max-length 3]] [[:default 99]]
- Map with override:  [{:validate :present? :message "required"}]

Standard refs ship in c3kit.apron.schema.refs (type predicates,
numeric, comparison/shape factories, string and type coercers,
:default). Modules register custom refs in their entry ns at
activation via (s/register-ref! :app/foo? {...}).

## Decisions

1. **Parse only, no discovery.** This bead exposes a pure
   read-manifest (path -> validated map). Walking a modules
   directory, classpath integration, and id/entry uniqueness
   checks live in a separate discovery bead.
2. **:extends is opaque to the manifest schema.** Outer keys are
   namespaced by ext-type; inner keys are impl names; value shape
   is ext-type-specific. Each ext-type validates its own slice
   when a registration arrives. The manifest schema validates only
   that :extends is a 2-level map of keyword -> keyword -> any.
3. **No separate :schema field.** Schema fragments for an impl
   live with the impl in :extends. One source of truth per impl.
4. **:entry is a namespace symbol.** Activation requires the ns
   and calls -init if present. Optional convention.
5. **:requires lists hard semantic deps; ids only.** No version
   constraints. Topo-sorted at activation. Empty by default.
6. **Unknown top-level keys: tolerated, stripped, warned.**
   c3kit drops unknowns; the manifest reader logs a warning so
   forward-incompat is visible.
7. **:id is canonical.** Authoritative over file location.
   Collision detection lives in discovery.

## Host duties

- isaac.module.manifest:
  - manifest-schema (c3kit schema for the manifest itself)
  - read-manifest: slurp + edn/read-string + conform to schema
  - throws clear errors on invalid shape; warns on unknown keys
- After composing core + module schema fragments (later beads),
  host calls c3kit.apron.schema/verify-schema-refs to fail fast on
  unregistered or wrong-slot refs.
- Host may bind c3kit.apron.schema/*ref-registry* to isolate a
  module's registrations (test/sandbox).

## Out of scope (separate beads)

- Discovery (find module.edn files; classpath integration; id and
  entry uniqueness checks across discovered manifests)
- Activation (require :entry, call -init, topo-sort :requires)
- Per-ext-type value contract (defined where the ext-type lives)
- Lazy/on-demand activation (P2)
- Version constraints in :requires

## Tests

This bead ships unit specs only. Manifest parsing has no
user-observable behavior on its own — it's plumbing called by
config-load. Integration scenarios live in fk45 (P1.4: wire
:modules into root config), where users actually see modules
appear in loaded config.

Unit specs cover:

- Valid manifest parses to the expected map
- Missing :id rejects with a clear error
- Missing :entry rejects with a clear error
- Unknown top-level keys are stripped with a warning
- :extends value with :validations [:positive?] roundtrips
- :extends value with factory ref [[:> 5]] roundtrips
- :extends value with unregistered ref fails verify-schema-refs

## Reference manifest (for fk45 scenarios + spec fixtures)

A whimsical "carrier pigeon" comm module is the canonical example.
Shape mirrors a real comm impl one-for-one (loft = token,
max-bytes = message-cap, allow-from {:lofts :keepers} =
allow-from {:guilds :users}, keeper = crew):

  {:id          :isaac.comm/pigeon
   :version     "0.1.0"
   :description "Carrier pigeon comm — message delivery via trained avian"
   :entry       isaac.comm.pigeon
   :requires    []
   :extends
   {:comm
    {:pigeon
     {:loft       {:type :string :validations [:present?]}
      :max-bytes  {:type :int    :coercions [[:default 140]]}
      :allow-from {:type :map
                   :schema {:lofts   {:type :seq :spec {:type :string}}
                            :keepers {:type :seq :spec {:type :string}}}}
      :keeper     {:type :string}}}}}

This manifest is used both by 1861's unit specs and as the fixture
for fk45's feature scenarios.

