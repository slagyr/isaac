---
# isaac-3z1b
title: Foundation's handbook chapter (the introduction)
status: draft
type: feature
priority: normal
created_at: 2026-09-29T23:50:57Z
updated_at: 2026-09-29T23:50:57Z
---

## Ruling

Micah, 2026-09-29 (see isaac-handbook bean 1's Ruling). Every installed
module MAY ship a `:handbook` chapter; foundation's comes first, as the
handbook's introduction, since foundation owns the concepts every other
chapter assumes (root, config, modules).

## Problem / Why

The `:handbook` manifest key, `module-report`, and `modules show` all
exist and work (isaac-gp4g, isaac-ppyj — both `completed` on
`isaac-foundation` main). Foundation itself doesn't use the key on its
own manifest yet — `src/isaac-manifest.edn` has no `:handbook` entry and
no chapter file exists. Without it, `handbook__read`'s table of contents
has a hole where the introduction should be.

## Wanted

- `isaac-foundation/src/isaac-manifest.edn` gains `:handbook
  "isaac/foundation/handbook.md"` (classpath resource under `src/`,
  matching the `:paths ["src"]` in `deps.edn`).
- The chapter file itself, free-form markdown (no fixed heading set),
  covering the concepts foundation owns, each concept with a
  `### Troubleshooting` subsection. Outline below — Micah reviews for
  accuracy before it ships; this bean drafts the outline only, the worker
  fills it in against real behavior at implementation time.
- Fix the stale docstring in `src/isaac/module/manifest.clj:22-26`: it
  still says the handbook doc is "conventionally handbook.md, with fixed
  `## Purpose` / `## Procedures` / `## Emergencies` headings" — copied
  verbatim from the old `:manual` wording by isaac-ppyj's rename and never
  revisited against this session's free-form-chapters ruling. Update the
  comment to describe the real convention (free-form, concept headings,
  `### Troubleshooting` subsections) instead of the retired fixed set.

## Chapter outline (draft — grounded in current foundation source; Micah
must confirm accuracy before this ships as real chapter text)

1. **Root layout** — `isaac.config.root`: where Isaac keeps config and
   state on disk, and the lookup chain that decides it (`--root` flag →
   test-injection → `ISAAC_ROOT` env → `~/.config/isaac.edn` →
   `~/.isaac.edn` → `~/.isaac` default).
   - Troubleshooting: "which root is this command actually using?" —
     check the lookup chain in order; a stray `ISAAC_ROOT` in the
     environment beats a `--root` flag typo into looking like it worked.

2. **Config composition** — `isaac.edn` at the root, plus per-entity files
   under `config/<kind>/<id>.edn` (crew, providers, etc.), plus a
   markdown companion file beside an entity for prose fields (souls,
   prompts) — inline value or `.md` sidecar, never both silently merged
   (`isaac.config.companion`). `${VAR}` substitution pulls from the
   process environment and a locked `<root>/.env` snapshot taken at load
   time (`isaac.config.env`).
   - Troubleshooting: a `${VAR}` that shows as `<VAR:UNRESOLVED>` means
     the var isn't set in either the environment or `.env` at load time —
     editing `.env` after boot doesn't help until reload; an inline field
     value silently wins over a same-named companion file, so an edit to
     the `.md` that "isn't taking" is usually a leftover inline value.

3. **Schemas** — config tables are c3kit apron schemas, composed from
   every installed module's `:isaac.config/schema` contribution
   (`isaac.config.schema-compose` / `schema-base`); `isaac config schema`
   walks the live composed root, not a hand-written doc.
   - Troubleshooting: a field that "isn't in the schema" after adding a
     module is usually a module that isn't actually installed/discovered
     yet (see Modules & berths) rather than a schema bug — check `isaac
     modules list` first.

4. **Modules & berths** — a module is a manifest (`isaac-manifest.edn`)
   naming an id, version, factory, and its contributions; a berth is a
   named extension point one module declares and others contribute into.
   Two collision rules: a *structural* clash (two modules declaring the
   same berth, or the same config-table shape) is an error; a *named*
   clash (two modules contributing the same tool/comm/command name) lets
   the later module in `:modules` order win, logged as an override
   warning. A missing `:handbook` resource is a warning, never an error —
   the module still loads.
   - Troubleshooting: "my module's contribution isn't showing up" —
     check `isaac modules show <id>` for what it declares/contributes and
     any warnings; a silently-overridden named contribution is usually a
     `:modules` ordering question, not a bug.

5. **Hot reload** — foundation owns noticing that config changed (it owns
   config and the daemon), watching every file the config layer
   recognizes: `isaac.edn`, every `<kind>/<id>.edn`, and their markdown
   companions, recursively under the config root. Default ON; `:hot-reload
   false` turns it off.
   - Troubleshooting: an edit that "isn't taking effect" — confirm
     `:hot-reload` isn't set to `false`, and that the edit landed under
     the watched config root (not a similarly-named file elsewhere).

## Acceptance

- `isaac/foundation/handbook.md` exists under `isaac-foundation/src/` with
  the five sections above (or Micah's revised set), each with a
  Troubleshooting subsection.
- `src/isaac-manifest.edn` declares `:handbook
  "isaac/foundation/handbook.md"`.
- `isaac modules show isaac.foundation` prints `Handbook:
  isaac/foundation/handbook.md` (text and `--edn`/`--json`) — this is
  existing `modules show` behavior (isaac-gp4g/isaac-ppyj already ship
  it); the only new fact is foundation's own manifest now sets the key.
- `src/isaac/module/manifest.clj`'s docstring no longer mentions
  `## Purpose` / `## Procedures` / `## Emergencies`.
- No new `.feature` scenarios needed — `modules_show_manifest.feature`
  already covers the `Handbook:` reporting behavior generically via the
  `marigold.longwave` fixture; this bean just exercises that same,
  already-tested path against a real module (foundation itself). If the
  worker wants a belt-and-suspenders check, a single new scenario
  asserting `isaac modules show isaac.foundation` prints the `Handbook:`
  line would do it — optional, not required for acceptance.

## Likely repo scope

`isaac-foundation` only: `src/isaac-manifest.edn`, new
`src/isaac/foundation/handbook.md`, `src/isaac/module/manifest.clj`
(docstring only).

## Notes

- No code dependency on isaac-handbook bean 1 or 2 — this is a content +
  one manifest-key change, entirely within already-shipped foundation
  plumbing (isaac-gp4g/isaac-ppyj). Can land independently and in
  parallel. Its practical value (showing up in a crew's `handbook__read`
  table of contents) only appears once bean 1 ships, but nothing here
  blocks on that.
- Micah's review gate applies to the CHAPTER TEXT specifically (accuracy
  against real behavior), not the mechanical manifest/docstring changes.

## Decisions (Micah, 2026-09-29)

- Topic ids: a chapter is its module id (`isaac.foundation`); a chapter section is `<module-id>#<slug>`; reference entries use `module:<id>`, `crew:<id>`, `config:<path>`.
- Foundation's chapter always leads the table of contents by an explicit rule (the handbook depends on foundation, so naming `isaac.foundation` here is fine); the other chapters follow sorted by module id.
- Secrets in the reference reuse `config get`'s existing redaction (`<VAR:redacted>`); no second "set / not set" signal.
- Size cap: config key `handbook.max-chars`, default 40000 characters.
- The 5-section outline stands; "config vs state discipline" goes inside Config composition. Micah reviews the written chapter for accuracy before it lands.
