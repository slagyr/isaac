---
# isaac-4cao
title: Manifest field schemas become first-class apron schemas (refs only)
status: draft
type: feature
created_at: 2026-05-18T22:07:53Z
updated_at: 2026-05-18T22:07:53Z
blocked_by:
    - isaac-vyz5
---

## Goal

Module manifest schemas — under `:comm <impl> :schema`, `:provider <impl> :schema`, `:tools <name> :schema`, `:slash-commands <name> :schema` — become first-class `c3kit.apron.schema` field specs, restricted to **refs only** (no inline function literals). Today each surface has a bespoke checker (`check-comm-slot`, `check-provider-type-fields`, `check-tools`/`required-tool-errors`, `check-slash-command-config`) that only enforces `:type` plus a custom `:required?` flag on tools. Replace all four with one apron-driven validation pass over the merged schema.

## Design

### Manifest field-spec contract

Manifest field specs are pure-data apron field specs with these restrictions:

- `:type` — apron's primitive types (`:string`, `:int`, `:keyword`, `:boolean`, etc.).
- `:validations` — vector of refs (either bare keyword like `:string?` or factory vector like `[:one-of? :a :b :c]`). NO inline `:validate` fns.
- `:coercions` — same shape as `:validations`. NO inline `:coerce` fns.
- `:message`, `:description` — string metadata.
- Drop `:required?` shorthand entirely. Use `[:present-when? :type :telly]` for type-conditional fields, `[:present?]` (or the bare `:present?` ref) for unconditional ones.

### Ref availability

All refs must already be registered in `c3kit.apron.schema`'s ref registry before any config is validated. The registry is populated by:

- **Apron's standard catalog** (`c3kit.apron.schema.refs/install!`) — `:string?`, `:integer?`, `:keyword?`, `:present?`, `:one-of?`, `:between`, `:max-length`, `:pattern`, etc.
- **Isaac core** — already registers `:llm-api-exists?`, `:tool-exists?`, `:provider-exists?`, `:manifest-provider-exists?`, `:comm-exists?`, `:model-exists?`, `:crew-exists?`, `:present-when?` (loader.clj:600–620).

**Modules may NOT register refs.** Isaac does not load module Clojure code at config-validation time — only the manifest EDN. If a module needs a ref that isn't yet in isaac core or apron's standard catalog, that's an upstream request, not module-private code.

### Loader plumbing

- Add `c3kit.apron.schema.refs/install!` call once at isaac startup (before any validation). Today only isaac's existence refs are registered.
- Replace `check-comm-slot` (loader.clj:818), `check-provider-type-fields` (loader.clj:863), `check-tools` / `required-tool-errors` (loader.clj:730–756), `check-slash-command-config` (loader.clj:760) with a single helper that calls `cs/validate` against the manifest's `:schema` map plus the user's slot config. Map the result's errors/warnings to isaac's `{:key path :value msg}` shape.
- Walk every loaded manifest at startup and call `cs/verify-schema-refs` against each `:schema` map. Any ref that isn't registered raises a clear error naming the module and the offending ref — fail-fast before the first config validation.
- `:tools :web_search :schema` in `src/isaac-manifest.edn` uses the non-apron `:known [:brave]` and `:required? true` — rewrite as `[:one-of? :brave]` under `:validations` and `[:present?]` (or move presence to a `:validations` entry).

### Telly manifest update

`modules/isaac.comm.telly/resources/isaac-manifest.edn` becomes:

```edn
{:id      :isaac.comm.telly
 :version "0.1.0"
 :comm    {:telly {:factory isaac.comm.telly/make
                   :schema  {:loft  {:type :string
                                     :validations [[:present-when? :type :telly]]}
                             :color {:type :string}
                             :mood  {:type :string
                                     :validations [[:present-when? :type :telly]
                                                   [:one-of? \"happy\" \"sad\" \"grumpy\"]]}}}}}
```

(`:mood` stays `:string` so existing reconciler scenarios that set `:mood happy` from a gherkin table — strings, not keywords — keep working.)

## Feature

`features/modules/schema_composition.feature` — seven new @wip scenarios after the existing trio:

1. `[:present-when? :type X]` errors when the field is omitted.
2. `[:one-of? ...]` rejects values outside the enum.
3. `[:one-of? ...]` accepts values inside the enum.
4. Provider field schema enforces types (kombucha's `:fizz-level :int`).
5. Tool field schema enforces presence (`:tools :web_search :api-key`).
6. Slash-command field schema enforces types (echo's `:command-name :string`).
7. A manifest referencing an unregistered ref fails fast at module activation with a clear error.

The feature header gains a paragraph documenting the refs-only policy.

## Prep changes (forward-compatible, NOT @wip)

These existing scenarios add `:loft` and swap `:wand`/`:fur` → `:color` so they pass both before and after this bean lands:

- `features/modules/activation.feature` — two scenarios now declare `:loft \"rooftop\"`.
- `features/lifecycle/reconciler.feature` — five scenarios add a `comms.<id>.loft` row; `:wand pink` and `:fur blue` swapped to `:color`.

## Acceptance

- [ ] Apron standard catalog installed at isaac startup.
- [ ] One apron-driven validation helper replaces `check-comm-slot`, `check-provider-type-fields`, `check-tools` (+ `required-tool-errors`), `check-slash-command-config`.
- [ ] `cs/verify-schema-refs` runs over every manifest at startup; unknown refs error with module + ref name.
- [ ] `modules/isaac.comm.telly/resources/isaac-manifest.edn` updated to the apron-refs shape above.
- [ ] `:tools :web_search :schema` in `src/isaac-manifest.edn` rewritten to apron-refs shape (drop `:known`/`:required?`).
- [ ] All seven @wip scenarios pass; `@wip` tags removed.
- [ ] All prep'd scenarios in `activation.feature` and `reconciler.feature` continue to pass.
- [ ] Existing `schema_composition.feature` scenarios continue to pass.
- [ ] Run: `bb features features/modules/schema_composition.feature features/modules/activation.feature features/lifecycle/reconciler.feature`

## Related

- Blocked-by: isaac-vyz5 (config set unknown-key cleanup) — establishes the loader-as-single-source pattern this bean extends.
- Blocks (follow-up): B2 — `config schema` consults module manifests; depends on the uniform apron shape this bean establishes.
