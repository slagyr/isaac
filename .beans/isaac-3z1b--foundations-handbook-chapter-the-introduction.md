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

## Chapter outline (revised with Micah, 2026-09-29)

Every section follows one pattern: what it is, how to change it with `handbook__configure`, how to verify, then **Troubleshooting**.

1. **Vocabulary**: the spaceship metaphor. Ship, bridge, comm, crew, quarters, modules, berths, hail and bands, soul, session, episode, turn.
2. **Runtime**: babashka or JVM (which one is live, what differs: startup, classpath/module loading, protocol-extension trap); server and service (process, launchd/systemd, logs; config hot-reloads, a restart is never the fix); components (what runs in the server, start/stop order).
3. **Files**: root layout (brief: operator concept); EDN format (keywords, maps, sets, strings, comments; common mistakes); markdown files (entity files with frontmatter, companion files for prose fields; unquoted `": "` in frontmatter).
4. **Config**: composition (`isaac.edn` + entity files + companions); paths and editing (dots, brackets, namespaced segments, set members; set/unset behavior: refusal, `--force` for required fields, confirmations; where a write lands); schemas (types, defaults, required, options, descriptions); effective vs written (defaults, coercion, unknown keys warned, `--raw`); secrets and `${VAR}` (never inline; `.env`; redaction; unresolved warns); templates (`:_base`); validation (errors vs warnings); hot reload.
5. **Modules and berths**: installed modules, contributions, versions and conflicts, named vs structural clashes, the `:handbook` doc.
6. **Scheduler**: tasks, triggers (interval, delay, at, cron), policies (coalesce, on-error, timeout).
7. **Logs**: streams (cli, server, module-added), levels, structured event names.
8. **Appendix**: the CLI and remote routing.

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
