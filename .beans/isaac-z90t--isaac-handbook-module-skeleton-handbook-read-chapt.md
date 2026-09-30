---
# isaac-z90t
title: 'isaac-handbook: module skeleton + handbook__read (chapters, TOC, multi-topic, size cap)'
status: completed
type: feature
priority: high
created_at: 2026-09-29T23:50:57Z
updated_at: 2026-09-30T00:41:08Z
---

## Ruling

Micah, 2026-09-29. Isaac gets an operating handbook for self-aware crews:
a new module, **isaac-handbook** (repo `slagyr/isaac-handbook`, "POH" —
Pilot Operating Handbook), providing `handbook__read` now and
`handbook__configure` (config writes) later, granted separately via a
crew's `:tools :allow`. The handbook explains how to operate Isaac, never
its source code.

## Problem / Why

There's no way for a crew to ask "how do I use this instance of Isaac?"
short of reading source across N repos. Each module already may know
things worth documenting (isaac-ppyj/isaac-gp4g landed the manifest-level
plumbing — `:handbook` classpath resource, `module-report`, `modules
show` — see Notes) but nothing surfaces that content to a running crew.
This bean builds the module and the read-only half of the tool.

## Wanted

- New repo `isaac-handbook`, laid out like `isaac-episodes` (foundation +
  agent deps, no isaac-http): `deps.edn` pinning `isaac-foundation` /
  `isaac-agent` shas + a `:dev-local` alias overriding to `../isaac-foundation`
  / `../isaac-agent` siblings; `bb.edn` with `spec` / `features` / `ci`
  tasks; `resources/isaac-manifest.edn` contributing `:isaac.agent/tools
  {:handbook__read {:factory isaac.handbook.tools/read-tool-factory}}`;
  gherclj wired the same way (`-s isaac.**-steps`, features under
  `features/`).
- `handbook__read(topics?: string[])`:
  - **No topics** → a table of contents: a short instance overview, then
    every available topic. v1 topics are chapter- and section-level only
    (module chapters + their concept sections); reference-entry topics
    (inventory/config) are added by the next bean without changing this
    tool's contract.
  - **Chapter topics**: one per installed module that declares
    `:handbook` in its manifest (via `isaac.module.berths/module-report`,
    already returns `:handbook`). Topic id = the module id (e.g.
    `marigold.charts`). Reading it returns the chapter's whole markdown.
  - **Section topics**: one per `##` (or top concept-level) heading inside
    a chapter. Topic id = `<module-id>#<slug>` (slug: lowercase, spaces →
    hyphens, punctuation stripped). Reading it returns just that section,
    including any nested `### Troubleshooting` subsection. Chapters are
    free-form — no fixed heading set, and Troubleshooting is a convention,
    not a requirement (a section without one is valid).
  - **Multiple topics** come back concatenated in the order requested.
  - **Unknown topics never fail the call** — each is listed as unknown,
    alongside a pointer to call `handbook__read` with no topics to see
    what's available.
  - **A total response size cap** (config, default TBD by the worker —
    exposed at `:handbook :max-chars` or similar): when the requested
    topics would exceed it, return what fits and list the rest as
    omitted (don't fail, don't silently truncate mid-topic).
  - Every returned item names its source module id.
- `handbook__read` is an ordinary tool: unavailable to a crew unless
  granted via `:tools :allow` (`handbook/read`), like any other tool.

## Acceptance

`isaac-handbook/features/read.feature` (drafted, not yet baselined — see
Notes) — 7 scenarios, currently `@wip`:
1. No topics → table of contents; a module with no `:handbook` contributes
   nothing.
2. A whole-chapter topic returns its markdown, naming its module.
3. A chapter-section topic returns just that section, with its
   Troubleshooting subsection.
4. Multiple topics come back in the order asked.
5. An unknown topic is listed as unknown; the call still succeeds.
6. The response is capped; omitted topics are listed by name.
7. `handbook__read` is absent from the prompt's tools unless the crew is
   granted `handbook/read`.

Baseline once the repo exists and `bb features` can actually run these
(this session could only dry-check step phrases against isaac-agent /
isaac-foundation's registered steps via `gherclj match` — the repo itself
doesn't exist yet to run the suite).

## Likely repo scope

New repo: `isaac-handbook`. No changes needed in `isaac-foundation` /
`isaac-agent` — `module-report`'s `:handbook` field and the
`:isaac.agent/tools` berth already exist.

## Notes

- **Already landed, don't re-do**: isaac-gp4g (manifest `:manual` +
  `module-report` introspection) and isaac-ppyj (renamed `:manual` →
  `:handbook` everywhere) are both `completed` on `isaac-foundation` main
  (`5ee1e6f...`). `module-report` already returns `:description`,
  `:handbook`, `:declares`, `:contributes` per module; `modules show`
  already prints `Handbook:`. This bean is purely additive on top.
- **Stale comment to fix while touching this**: `isaac-foundation`'s
  `src/isaac/module/manifest.clj:22-26` still says the handbook doc is
  "conventionally handbook.md, with fixed `## Purpose` / `## Procedures`
  / `## Emergencies` headings" — carried over verbatim from the old
  `:manual` docstring by isaac-ppyj's mechanical rename. That's stale
  against this session's ruling ("free-form... no fixed headings...
  Troubleshooting subsections"). Small foundation fix, folded into bean 3
  (foundation's own chapter bean) rather than here.
- All 7 scenarios reuse existing `isaac-agent` / `isaac-foundation` steps
  (verified via `gherclj match` against both repos' registered steps at
  `origin/main`: `isaac-foundation` `40a185f`, `isaac-agent` `a77bf6f`) —
  **zero new steps needed**. Module-contributed tools (like
  `recall__search` in isaac-episodes) can't be exercised via the direct
  `the tool "X" is called with:` step — that step clears the registry and
  re-registers only built-ins. Scenarios instead go through a real prompt
  turn: crew allows the tool, Grover queues a `toolCall` response, assert
  on `session "..." has transcript matching:`. Mirrors
  `isaac-episodes/features/recall/live_tools.feature` exactly.
- Fixture modules are local to isaac-handbook's own features (`/tmp/modules/...`,
  written by the scenario itself, same pattern as
  `isaac-foundation/features/module/modules_show_manifest.feature`) — not
  a dependency on isaac-foundation's `marigold.bridge`/`marigold.longwave`
  fixtures (those aren't used by any downstream module's own features
  today; they exist for foundation's own comm/berth tests). New fixture:
  **`marigold.charts`** (the ship's chart room — navigation references),
  with a real minimal `src/marigold/charts.clj` (`create-module` returns
  `{}`) so the module factory actually resolves during a live prompt
  turn, not just a read-only `modules show`. Flagging the new fixture
  name for veto.

## Decisions (Micah, 2026-09-29)

- Topic ids: a chapter is its module id (`isaac.foundation`); a chapter section is `<module-id>#<slug>`; reference entries use `module:<id>`, `crew:<id>`, `config:<path>`.
- Foundation's chapter always leads the table of contents by an explicit rule (the handbook depends on foundation, so naming `isaac.foundation` here is fine); the other chapters follow sorted by module id.
- Secrets in the reference reuse `config get`'s existing redaction (`<VAR:redacted>`); no second "set / not set" signal.
- Size cap: config key `handbook.max-chars`, default 40000 characters.

feature-baseline: isaac-handbook 6da4a9f77081299c0d1e31e1e492719882ffc112
feature-blob: isaac-handbook features/read.feature 4065ff0fc8bb72d3c39b4596dd113efe508d21cb

## Conflict — frozen Background fixture can't activate for a live turn (2026-09-30)

Worker (local, zanebot down). Module skeleton + `handbook__read` implementation
is done and unit-tested (`isaac-handbook` `bean/isaac-z90t`, pushed, not
landed): `deps.edn`/`bb.edn` pinned to isaac-foundation `53c8aa83627eb4334cec1e64c72ceb42bf17fb3e`
/ isaac-agent `a77bf6f55bf57f4617abc72271024d43b9710f02`, CI workflows,
`resources/isaac-manifest.edn` (`:isaac.agent/tools {:handbook__read ...}`,
`:isaac.config/schema {:handbook {:max-chars ...}}}`), and
`isaac.handbook.{sections,chapters,render,module,tools}` implementing the
reading/sectioning/TOC/foundation-first-ordering/size-cap logic, covered by
speclj specs in `spec/isaac/handbook/` (22 examples green via `bb spec`).

**`features/read.feature`'s 7 `@wip` scenarios cannot pass as written.** All 6
scenarios that configure the `marigold.charts` fixture module under `:modules`
fail identically: the whole CLI comes back with "Unknown command: prompt" and
an empty transcript (scenario 7, which never configures `marigold.charts`,
passes). Root cause, confirmed by reproducing the exact Background steps
directly against `isaac-foundation` `53c8aa8` / `isaac-agent` `a77bf6f`
(bypassing gherclj's own silent-catch in `isaac.main/register-module-cli-commands!`,
which swallows the exception and explains why every command — not just
`prompt` — disappears):

1. **mem-fs vs. real classpath.** The Background writes
   `/tmp/modules/marigold.charts/{deps.edn,src/marigold/charts.clj,resources/...}`
   via `the isaac file "..." exists with:` — which, under `default Grover
   setup`, writes only into the virtual `mem-fs` the gherclj harness installs
   (`isaac.foundation.root_steps/initialize-root!` with `virtual? true`).
   `isaac.module.lifecycle/instantiate-module!` activates any `:local/root`
   module that declares a `:factory` (`eager-load?`), which calls
   `isaac.module.classpath/ensure-module-deps!` → `add-libs`/`add-deps` —
   real-JVM-classloader operations that read real `java.io` files. They
   cannot see mem-fs-only content, so `(require 'marigold.charts)` fails:
   `java.io.FileNotFoundException: Could not locate marigold/charts__init.class,
   marigold/charts.clj or marigold/charts.cljc on classpath.` Every other
   module that exercises a *live prompt turn* with a marigold fixture
   (isaac-episodes' `recall/live_tools.feature`, isaac-google/isaac-cron/etc.)
   uses a **real, git-committed** fixture under `isaac-foundation/modules/marigold.*`
   (resolved as a real git/local-root coord onto real disk), never one
   written on the fly via the mem-fs `the isaac file ... exists` step. The
   only prior user of that step for a `:local/root` module
   (`isaac-foundation/features/module/modules_show_manifest.feature`) only
   exercises read-only introspection (`modules show`, `the config is
   loaded`), which never activates/instantiates the module.
2. **Fixture factory arity/return type.** Independent of (1): the
   Background's `src/marigold/charts.clj` is `(defn create-module [_opts]
   {})`. `instantiate-module!` calls the manifest `:factory` as `(factory)`
   — zero args — so this throws `ArityException: Wrong number of args (0)
   passed to: marigold.charts/create-module` (confirmed by re-running the
   repro with a real on-disk copy of the fixture). Even with the arity
   fixed, returning `{}` fails `module/module?` ("module factory returned
   non-Module") — every real fixture in the ecosystem
   (`isaac-foundation/modules/marigold.{bridge,longwave}`) is
   `(defn create-module [] (module/module))`.

Both are structural properties of the frozen Background text, not something
fixable from the `isaac-handbook` side — `handbook__read`'s own reads (of the
handbook markdown content) go through `isaac.module.coords/read-text-file`
against the ambient nexus `:fs` (mem-fs during a turn) and work fine; the
failure is entirely in module *activation*, forced merely by declaring
`marigold.charts` under `:modules` with a `:factory`, before `handbook__read`
ever runs.

Per the worker brief ("if one can't be satisfied as written, STOP and report
with evidence"), stopping here rather than editing the frozen `.feature` text
or re-baselining. Left `@wip` in place (scenarios still can't run); `bb ci` is
green on the unit specs. Possible fixes for the planner to choose among:
write the fixture's `deps.edn`/`src`/`resources` to the **real** disk (not via
the mem-fs `the isaac file` step) so `:local/root` activation can see it;
mark the fixture `:builtin?` some other way that skips eager activation; or
change what `marigold.charts` looks like (no `:factory`, so it never needs
activation — a plain manifest-only module, since `handbook__read` doesn't
need the module *running*, only its manifest + handbook resource, both readable
via mem-fs). The third option looks cleanest but would still need
`resources/marigold/charts/handbook.md` writable this way and the `deps.edn`/`src`
files dropped entirely from the Background.

Branch `bean/isaac-z90t` pushed to `isaac-handbook` with the skeleton +
implementation + unit specs (not merged to main; CI on that branch does not
run per the repo's CI-on-main-only convention). `isaac-handbook.modules.edn`
registry entry in this repo intentionally NOT added yet (no landed main sha).

feature-baseline: isaac-handbook 52c00aff4375278f2f73fde3776ba59444963c2d
feature-blob: isaac-handbook features/read.feature 2cfcee14f317f4ed8936fc522638c3c413152969

## Landed on main (2026-09-30)

main-sha: isaac-handbook 476a8cc96cbe9bc6333ba40d2da58bd31eebcc29

Re-baselined fixture (manifest-only marigold.charts/bridge/longwave, no
:factory/deps.edn/src) fixed the activation conflict from the earlier note.
All 7 scenarios green (`bb features`), plus 29 unit-spec examples
(`bb spec`) including new specs for `isaac.handbook.tools` and
`isaac.handbook.module`. `bb bean-gate verify isaac-z90t --dir
isaac-handbook=... --ref isaac-handbook=main` passed at 476a8cc. GitHub
Actions CI green on main (run 36651304234). Added `:isaac.handbook` to
`isaac/modules.edn` (this commit) pointing at the same main sha — every
other module in that registry is registered at creation time, so
isaac-handbook follows suit. Registry-only: no `modules install`/`upgrade`
run on zanebot or yopp, per the brief.
