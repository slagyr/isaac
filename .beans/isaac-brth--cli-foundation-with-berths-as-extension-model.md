---
# isaac-brth
title: CLI-as-foundation with berths as the extension model
status: draft
type: refactor
priority: normal
created_at: 2026-05-21T20:58:30Z
updated_at: 2026-05-25T00:04:03Z
---

## Motivation

Today's structure has the full isaac platform as the foundation: anyone
installing isaac drags the whole stack along — config loader, session store,
LLM dispatch, tool registry, comm protocol, server — even when they only
want client-side commands like `chat` or the `acp --remote` proxy.

The proposed inversion makes the **CLI dispatcher** the foundation. Everything
else — the server, LLM dispatch, session storage, comms — becomes a module
that plugs in. `brew install isaac` gives you a binary that can discover and
install modules, dispatch their commands, and not much else. The full
platform is `isaac-server`, installed like any other module.

The wins, ranked honestly:

- **Architectural** (primary): true symmetry — the platform isn't privileged,
  it uses the same extension API everyone else does. Modules can declare new
  berths, not just contribute to ones the foundation hardcoded.
- **Operational**: machines that don't run the server can skip the LLM
  dispatch, session store, comm runtime, etc. The relevant code paths simply
  aren't loaded.
- **Packaging** (TBD): the foundation may also be small enough to ship as
  a thin binary. Size delta should be **measured** before this is claimed as
  a benefit — the bb runtime, schema lib, scheduler, and shared utilities
  may dominate the install regardless of what's in or out.

## Vocabulary

- **Berth** — a named extension point declared by a module. The foundation
  has exactly one built-in berth (`:cli`). Other modules introduce more
  (e.g., `isaac-server` declares `:isaac.server/comm`, `:isaac.server/tools`,
  `:isaac.server/route`, ...). Berths are namespaced by the providing
  module's id.
- **Contribution** — a module's entry at a berth's key in its manifest.
  e.g., isaac-discord contributes `{:isaac.server/comm {:discord {…}}}`.
- **Foundation** — the small core (CLI dispatcher + module loader + shared
  utilities). The piece that's `brew install isaac`.
- **Provider module** — a module that *declares* berths (e.g., isaac-server).
- **Consumer module** — a module that *contributes* to one or more berths
  (e.g., isaac-discord contributing to isaac-server's comm berth).

## Anatomy of a berth

A berth is a *bundle of contracts*. Three fields are required; the rest are
optional and depend on the kind of extension being modeled.

| Field              | Required? | What it does                                                                |
|--------------------|-----------|-----------------------------------------------------------------------------|
| `:contract-version`| yes       | Semver of the berth's contract. Consumers declare compatibility.            |
| `:description`     | yes       | Human-readable; explains intent and lists domain protocols (next field).    |
| `:entry-schema`    | yes       | Validates each module's contribution at this berth.                         |
| `:lifecycle`       | yes       | Names a foundation-owned instantiation policy. *Implies* required protocols.|
| `:satisfies`       | sometimes | Domain protocol vars the foundation enforces at `:register-fn` time.        |
| `:config-slice`    | sometimes | Where in user config slots live for this berth (path + per-slot schema).    |
| `:register-fn`     | sometimes | Symbol the foundation calls to publish a live instance to a registry.       |
| `:deregister-fn`   | sometimes | Symbol the foundation calls on shutdown/uninstall.                          |
| `:validation-refs` | sometimes | Schema validation refs the berth publishes for cross-references.            |

**Lifecycle vs. domain protocols.** Two distinct kinds of protocol
requirement; one is foundation-policy, the other is berth-policy:

- *Lifecycle-required protocols* are baked into the `:lifecycle` value. E.g.,
  `:slot-tree` implies the factory's output must satisfy `Reconfigurable`
  (the policy calls `on-startup!` / `on-config-change!`). You don't list
  these — they're an inseparable part of the policy you picked.
- *Domain protocols* (`Comm`, `Api`, `Tool`, …) are berth-specific contracts
  the foundation can't infer. They're declared in `:satisfies` and enforced
  via `(satisfies? p inst)` at register time. Failure → clear error, before
  any consumer code calls a method on a misbehaving instance.

Example — `:isaac.server/comm` berth:

```clojure
{:isaac.server/comm
 {:contract-version "1.0"
  :description      "Communication channels (Discord, iMessage, ACP, ...).
                     :slot-tree gives you the lifecycle (on-startup!,
                     on-config-change!); :satisfies enforces the domain
                     contract."
  :entry-schema     {:type     :map
                     :key-spec {:type :keyword}
                     :value-spec {:type :map
                                  :schema {:factory {:type :symbol :validate present?}
                                           :schema  {:type :map}}
                                  :require-namespaced-keys true}}
  :config-slice     {:path   [:comms]
                     :schema {:type     :map
                              :key-spec {:type :keyword}
                              :value-spec {:type :map
                                           :impl-schema-via :type}}}
  :lifecycle        :slot-tree
  :satisfies        [isaac.comm/Comm]
  :register-fn      isaac.comm.registry/register-instance!
  :deregister-fn    isaac.comm.registry/deregister-instance!}}
```

A consumer module's contribution carries the version constraint inline.
The foundation derives the requires-graph from the contribution keys; no
separate `:requires-berths` map, no two-places-to-keep-in-sync:

```clojure
{:id :isaac.comm.discord
 :isaac.server/comm
 {:contract "^1.0"                        ; <- version constraint right here
  :entries {:discord {:factory isaac.comm.discord/make
                      :schema  {:discord/token       {…}
                                :discord/allow-from  {…}}}}}
 :isaac.server/route
 {:contract "^1.0"
  :entries  {[:get "/discord/healthcheck"] isaac.comm.discord/health-handler}}}
```

The contribution at each berth key is `{:contract <semver> :entries <data>}`.
`:contract` is reserved (un-namespaced, per the same convention berths use);
`:entries` is what `:entry-schema` validates. Foundation walks `keys`, knows
which berths the module requires, checks the `:contract` against each
provider's `:contract-version`, and refuses activation with a named-module
hint when something doesn't line up.

**Semver rules** the foundation enforces for `:contract-version`:

- **Major bump**: anything that breaks existing consumers. Removing a key
  from `:entry-schema`, changing `:lifecycle`, changing `:satisfies` to
  require a new protocol, changing `:impl-schema-via`, renaming the
  discriminator, changing a `:validation-refs` source.
- **Minor bump**: backward-compatible additions. New optional
  `:entry-schema` fields, new `:validation-refs` entries, new
  `:satisfies` entries that are inhabited by the existing instances
  (rare; usually a new protocol = major).
- **Patch**: docs, examples, internal foundation-side improvements.

Constraint syntax is conventional semver: `"^1.0"` = "≥1.0, <2.0";
`"~1.2"` = "≥1.2, <1.3"; `"1.0"` = exact.

## Design choices made

1. **Namespaced berth names** (`:isaac.server/comm`, not `:comm`). Costs
   nothing today, prevents future name conflicts, and signals ownership.
2. **Requires are derived from contribution keys**; the version constraint
   hangs off the contribution itself (`{:contract "^1.0" :entries {…}}`).
   No `:requires-berths` map. One source of truth, no drift between a
   separate "I depend on these" list and the actual contributions.
3. **Keep `:type` as the slot discriminator.** Earlier drafts proposed
   renaming to `:impl`. Dropped on review: `:type` is in user-facing config
   today for comm slots (`src/isaac/config/schema.clj`) and for provider
   manifest-template inheritance — renaming requires a real migration
   (accept-both period, deprecation, doc updates). The overloading is
   annoying but not fatal; not worth spending the migration budget here.
   If we ever do rename, it's its own bean with a real compat plan.
4. **Berth-reserved keys via namespacing**. Berth `:value-spec` can set
   `:require-namespaced-keys true`, meaning all per-impl schema keys must be
   namespaced. Un-namespaced keys (`:impl`, future `:enabled?`, etc.) are
   reserved for the berth. No per-berth constraint code needed.
5. **Foundation owns the vocabulary, modules pick from it. No escape
   hatches.** Two small vocabularies the foundation knows:
   - **Lifecycle policies**: `:singleton`, `:slot-tree`, `:declarative`,
     `:stateless-factory` (probably 5-7 once we write real berths). Each
     policy bundles the protocols it requires of the factory's return value.
   - **Validation-ref sources**: `:berth-contribution-keys`,
     `:config-slice-keys`, possibly `:union`. Two cover every existence ref
     in current isaac.

   If a module needs a novel lifecycle or source, that's a foundation PR,
   not a per-module workaround. Eclipse's failure mode was hundreds of
   extension-point shapes accumulating over years; closed vocabulary with
   slow extension via the foundation is the discipline that prevents that.
6. **Module discovery list**: a curated EDN file in the foundation
   (`isaac-cli/modules.edn` or similar) — like a tiny brew formula directory.
   Updateable via PR. No registry server.
7. **Shared kitchen namespaces stay in the foundation**: `isaac.system`,
   `isaac.fs`, `isaac.logger`, the schema runtime, `isaac.scheduler`. These
   have to be reachable to all modules; can't live in `isaac-server` because
   consumer modules need them and might not have isaac-server installed.
   Their *namespaces* are foundation-shipped; their *running instances* are
   established by whichever module needs them at boot (see "Foundation
   utilities and their instances" below).

## Foundation utilities and their instances

The foundation *ships* a small kit of utility namespaces. It does **not**
generally instantiate the runtime values inside them. The pattern: a utility
ns exposes constructors (e.g., `scheduler/create`, `scheduler/start!`); a
consumer module — usually isaac-server, sometimes a private one in a thin
CLI — stands up an instance and registers it via `system/register!`. Other
modules pull it via `(system/get :scheduler)`.

| Utility                  | Instantiator today                | After the split                                 |
|--------------------------|-----------------------------------|-------------------------------------------------|
| `isaac.scheduler`        | `isaac.server.app/start!`         | isaac-server bootstrap, OR a private one        |
|                          |                                   | (e.g., acp's proxy creates its own pool=1)      |
| `isaac.fs/*fs*`          | implicit (real fs); tests rebind  | unchanged                                       |
| `isaac.system` registry  | `isaac.system/init!`              | invoked by foundation CLI dispatcher at start   |
| `isaac.logger`           | `log/set-output!` per process     | unchanged                                       |
| Schema runtime           | `isaac.config.loader` refs install | foundation init; berths register refs into it  |

This means consumer modules that *use* the scheduler (e.g., a hypothetical
`isaac.jobs` module contributing recurring tasks to an isaac-server-provided
`:isaac.server/scheduler` berth) get the shared one for free. Modules that
need their own (acp proxy, single-thread, scoped to one CLI invocation)
build their own. No accidental coupling.

**Footgun rule**: private instances stay *module-local* — they do NOT call
`system/register! :scheduler …` (or any other shared-slot key). The
`system/` slot for a given utility belongs to whichever module bootstraps
the platform (typically isaac-server). A private acp scheduler is a
locally-held value passed through closures; it doesn't appear in
`isaac.system` and therefore can't clash with isaac-server's later
registration in the same process.

## Open cracks (acknowledged, not solved)

1. **Schema validation refs come from berths.** New mechanism: berths publish
   `:validation-refs` into a foundation-owned schema runtime. Other berths'
   schemas can reference them by namespaced keyword. The runtime resolves
   each ref against the live module index. Direct migration of current
   seven existence refs:

   | Today's ref                   | Source                        | Owning berth          |
   |-------------------------------|-------------------------------|-----------------------|
   | `:tool-exists?`               | `:berth-contribution-keys`    | `:isaac.server/tools` |
   | `:comm-exists?`               | `:berth-contribution-keys`    | `:isaac.server/comm`  |
   | `:llm-api-exists?`            | `:berth-contribution-keys`    | `:isaac.server/llm-api` |
   | `:manifest-provider-exists?`  | `:berth-contribution-keys`    | `:isaac.server/provider` |
   | `:provider-exists?`           | `:config-slice-keys`          | `:isaac.server/provider` |
   | `:model-exists?`              | `:config-slice-keys`          | `:isaac.server/model` |
   | `:crew-exists?`               | `:config-slice-keys`          | `:isaac.server/crew`  |

   Note that **one berth can publish multiple refs** drawing from different
   sources — the provider berth publishes both `:manifest-provider-exists?`
   (templates declared in manifests) and `:provider-exists?` (providers
   configured by the user). The two-source vocabulary still covers all
   seven cleanly.
2. **Install-time UX for missing providers.** If isaac-discord contributes
   to `:isaac.server/comm` but isaac-server isn't installed, what should
   happen? This is the **most novel and least precedented surface in the
   design** — Eclipse's resolution-failed messages are famously bad, NixOS's
   type errors are notoriously hostile, k8s gives dry "no CRD found." We
   should do this as a real design exercise, with mockups, **before any
   berth migration begins**. See migration phase 2.
3. **Lifecycle vocabulary completeness.** The four named policies cover all
   current kinds, but lazy activation (a tool registered only when first
   called) and per-session instances aren't there. Likely 5-7 once written
   for real.
4. **Berth name collisions.** Two modules declaring the same namespaced
   berth = conflict. Detect at module-discovery time, refuse to activate
   either until the user resolves it.

### Resolved in v1 (not deferred)

- **Lifecycle ⇄ protocols coupling.** Folded into `:lifecycle` itself —
  policies bundle their required protocols. The bean's berth schema no
  longer presents them as orthogonal.
- **Berth versioning.** `:contract-version` is required at v1, not deferred.
  Discord/iMessage rebases have demonstrated the AbstractMethodError pain
  already — deferring would just inherit it into the new design.

## Migration sketch

Phased, additive, each phase reversible:

1. **Add `:berths` and `:contract-version` to the manifest schema**
   (additive — no module is required to use them yet). Foundation knows
   about the fields but does nothing with empty maps. Includes
   `:require-namespaced-keys` as a schema primitive.
2. **Design install-time UX** (design exercise, no code). Mock up
   `isaac modules install`, `modules list`, error states for missing
   providers, version-mismatch messages. This is genuinely novel surface
   — there's not a great precedent to lean on. The output of this phase
   is mockups + decisions, not running code, and the decisions might
   reshape later berth schema details. **Front-loaded deliberately**;
   doing this last would risk discovering UX constraints that force
   schema rework.
3. **Measure foundation/platform size split.** Inventory what a stripped
   foundation actually contains (foundation utilities + cli dispatcher
   + module loader + their transitive deps) versus what isaac-server
   would carry. Captures the actual size delta before later phases
   bake assumptions about it.
4. **Re-declare `:cli` as a berth to prove the loop end-to-end.** Bigger
   than it first looks. The current CLI path is hardcoded in *three*
   places, not one:

   - `isaac.module.loader` knows the `:cli` extension kind.
   - `isaac.main` statically requires the built-in command namespaces
     (`isaac.comm.acp.cli`, `isaac.bridge.chat-cli`, etc.) so their
     `(register! …)` calls fire at load.
   - `isaac.main/register-module-cli-commands!` runs its own discovery
     pass before subcommand dispatch (it has to — CLI dispatch precedes
     server boot, so it can't use the configurator/activate! path).

   To actually prove the loop, this phase needs:

   - **4a.** Add `:cli` as a berth in a foundation-internal manifest.
     Wire `isaac.module.loader` and `isaac.main` to read it.
   - **4b.** Convert at least one built-in command (probably `init`,
     since it has no module dependencies) from static-require + side-effect
     `register!` to a manifest-declared berth contribution. Demonstrates
     that the same mechanism third parties use can host built-ins.

   Without 4b, we've renamed a code path without showing the symmetry
   works. **Can run in parallel with phases 2 and 3** — UX mockups and
   size measurement don't gate the schema-and-loader work, and shipping
   a concrete artifact early surfaces design-vs-code gaps before they
   compound.
5. **Migrate a *declarative* berth**: `:route`. No factories, no
   protocols, no config-slice — just a map of `[method path] -> handler`.
   Validates the `:lifecycle :declarative` policy and exercises the
   `register-fn` symbol resolution.
6. **Migrate `:tools`**, which exercises the trickiest piece:
   `:validation-refs` with `:source :berth-contribution-keys`. The
   `:tool-exists?` ref currently in `isaac.config.loader` moves to the
   tools berth. Other code that uses it stays untouched.
7. **Migrate `:provider`, `:llm/api`, `:slash-commands`, `:hook`** — same
   shape variations as comm/tools, mostly mechanical.
8. **Migrate `:comm`** — the most complex (slot-tree lifecycle,
   Reconfigurable protocol, impl-via discriminator). All earlier phases
   de-risk this one.
9. **Foundation/platform split** (deferred until 4–8 are done and proven).
   Create `isaac-server` module repo; move everything except the
   foundation pieces. The shrunken `isaac` repo becomes the foundation.
   Consumer modules (discord, imessage, acp) bump their isaac-server dep —
   they were already declaring contributions through berths.
10. **Optional slim CLI packaging**. Only worth doing if phase 3's
    measurement showed a meaningful size delta. Otherwise the design wins
    are already realized inside a single repo.

Each step has its own validation gate: `bb spec` and `bb features` green
in isaac plus every module repo that depends on the migrated berth. No
big-bang.

**Don't commit to the foundation/platform split (phase 9) until phases
4–8 are done.** Even after the berth mechanism is fully retrofitted in
core isaac, one cohesive repo is a fine resting state. The architectural
wins (symmetric extension, modules declaring new berths, validation refs
as data) are realizable inside a single repo. Phase 9 is packaging, not
correctness.

## Acceptance criteria

The whole sweep is bigger than one bean's worth. Concretely, this bean
is "done" when:

- The design choices above are reviewed and either confirmed or amended.
- Phases 1, 2, 3, and 4 (manifest field, install-time UX design, size
  measurement, `:cli` berth re-declaration) each have a child bean.
  Phases 2–4 can proceed in parallel after phase 1. The remaining
  phases get child beans as their predecessors land — don't try to plan
  all 10 up front.
- Phase 1 (`:berths` + `:contract-version` in manifest schema, additive)
  is implementable without surprises — i.e., the design holds up under
  one round of concrete code-level scrutiny.

## Precedent (very brief)

- **Eclipse extension points**: closest direct match in concept — modules
  declare new extension shapes others contribute to. Steal: lifecycle
  states (`resolved`/`started`/`stopped`), introspectable registry.
- **NixOS modules**: declarative typed options, modules declaring options
  others fill in. Steal: mandatory option descriptions feeding docs.
- **Kubernetes CRDs**: extensible resource definitions with own schema.
  Steal: schema versioning + conversion model (defers our crack #4).
- **VSCode contributions**: less symmetric (foundation owns all contribution
  points), but worth knowing for introspectable CLI surface
  (`isaac modules list`).
- **Terraform providers**: foundation knows nothing about specific resources;
  providers define schemas. Spirit yes, mechanism no.
- **Clojure ecosystem primitives**: Integrant for `:singleton` and
  `:slot-tree` lifecycle implementations, Malli or apron-schema for the
  schema runtime. Useful libraries, not whole-system replacements.

Nothing existing maps cleanly to our constraints (bb-native, small, both
CLI-and-server, Clojure end-to-end). The synthesis above is largely
theft from the references plus the small twist of symmetric berth
declarations.
