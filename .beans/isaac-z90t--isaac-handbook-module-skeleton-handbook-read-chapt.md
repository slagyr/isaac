---
# isaac-z90t
title: 'isaac-handbook: module skeleton + handbook__read (chapters, TOC, multi-topic, size cap)'
status: todo
type: feature
priority: high
created_at: 2026-09-29T23:50:57Z
updated_at: 2026-09-29T23:50:57Z
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
