---
# isaac-o9np
title: Hail bands missing :frontmatter? — <name>.md with frontmatter doesn't load (and warns dangling)
status: completed
type: bug
priority: normal
created_at: 2026-06-25T17:10:34Z
updated_at: 2026-06-25T17:38:03Z
---

Hail band config supports a companion `<name>.md` (prompt body) but NOT a self-contained `<name>.md` with YAML frontmatter as the whole band entity. So bands authored as frontmatter-md don't load, and `config validate` flags them `dangling: no matching hail entry`.

## Diagnosis
- Hail schema descriptor (isaac-hail `src/isaac-manifest.edn`, `:isaac.config/schema {:hail {...}}`) declares `:entity-dir "hail"` + `:companion {:field :prompt, :mode :optional}` but is **missing `:frontmatter? true`**. Cron has it (`:entity-dir "cron", :frontmatter? true, :companion {...}`); crew uses frontmatter for souls.
- `:frontmatter? true` is what tells the loader a `<name>.md` with YAML frontmatter is a COMPLETE entity (frontmatter = entity fields, body = the `:prompt` companion field). Without it, every `.md` is treated as a body-only companion requiring a separate `<name>.edn`.
- Consequence (two symptoms, one cause): (1) bands authored as frontmatter-md NEVER LOAD (frontmatter ignored, no band); (2) `dangling-md-warnings` (foundation `config/loader.clj:620`) flags them because no matching `.edn`/inline entry exists (`entity-files` only returns `.edn` for hail without the flag, so `file-ids` is empty).

## Evidence (zanebot)
`config/hail/` has 6 `.md` files, zero `.edn`, no inline `:hail`. e.g. `isaac-work.md` frontmatter: `crew: scrapper / session-tags: [:isaac] / reach: :one` + prompt body. All 6 warn dangling; none load as bands.

## Fix
Add `:frontmatter? true` to the hail schema descriptor in isaac-hail `src/isaac-manifest.edn` (between `:entity-dir` and `:companion`, matching cron). Then `<name>.md` loads as a band from frontmatter + prompt body; `entity-files` counts it; the dangling warning disappears.

## Separate cleanup (note, not part of fix)
`old-bean-work.md` / `old-bean-verify.md` use `crew-tags: [:role/worker]` in frontmatter, but `crew-tags` was REMOVED in the hail routing redesign (isaac-u5tj). Once frontmatter loads they'd fail validation (unknown band field). Migrate to `session-tags` or delete. The `isaac-*` files use current fields and are fine.

## Scenarios
Under review with Micah (one at a time) — to land in isaac-hail `features/bands.feature` (@wip). Recorded here as approved.

## Acceptance
- A hail band defined solely as `config/hail/<name>.md` (frontmatter fields + prompt body) loads as a band (frontmatter -> band config, body -> :prompt).
- `config validate` produces NO `dangling: no matching hail entry` warning for a frontmatter-md band.
- Companion-only `.md` (body, with a matching `.edn`/inline band) still works (existing behavior preserved).
- Feature coverage in bands.feature (@wip) green.

## Notes
Surfaced 2026-06-25 on zanebot `isaac config validate`. Foundation-mechanism is fine (crew/cron use it); the bug is hail not opting into frontmatter.

## Implementation note (2026-06-25)
The manifest fix is already staged (uncommitted) in the local isaac-hail checkout as part of this work: `src/isaac-manifest.edn` adds `:frontmatter? true,` to the hail schema; `features/bands.feature` description updated. Scenarios authored below build on that (NOT a separate agent).

## Implemented + scenarios locked (2026-06-25)
Changes made in isaac-hail (build on the staged :frontmatter? true):
- `src/isaac-manifest.edn`: `:frontmatter? true` on hail schema (was staged); REMOVED the `:crew-tags` schema entry (the `:retired?` special-case). crew-tags is now just an unknown key (warning), not a hard reject — stale bands still fail via `[:requires-any? :session :session-tags]`.
- `spec/isaac/config/hail_loader_spec.clj`: removed "rejects retired :crew-tags" test.
- `features/bands.feature`: removed the crew-tags-retired scenario; added 3 @wip scenarios.

Scenarios (approved, in bands.feature @wip):
1. Band defined as a single `<name>.md` with YAML frontmatter validates OK + stdout has NO "dangling".
2. Frontmatter band is schema-checked: a type conflict (`reach: 5`, expects keyword) is rejected (stderr "reach", exit 1) — proves frontmatter parsed as band config; no crew-tags.
3. Body-only `.md` + matching `.edn` band still validates OK, no dangling (companion regression guard).

DoD: run `bb features features/bands.feature` (incl wip) + `bb spec spec/isaac/config/hail_loader_spec.clj` green, then remove @wip.

Separate cleanup (not this bean): zanebot old-bean-work.md / old-bean-verify.md use crew-tags + no session-tags — delete or migrate to session-tags.

## Summary of Changes (2026-06-25)
Implemented + verified GREEN in isaac-hail (committed):
- `src/isaac-manifest.edn`: `:frontmatter? true` on hail schema; REMOVED the `:crew-tags` schema entry + its `:retired?` validation.
- `spec/isaac/config/hail_loader_spec.clj`: removed the retired-crew-tags test.
- `features/bands.feature`: removed crew-tags scenario; added 3 scenarios (frontmatter band loads + no dangling; frontmatter schema-checked via `reach: 5` type conflict; body-only .md companion still works). @wip removed — they PASS.

Verification: `bb features features/bands.feature` = 6 examples, 0 failures; `hail_loader_spec` = 4 examples, 0 failures. (Lint shows pre-existing speclj/kondo unresolved-symbol noise, not introduced here.)

NOT yet deployed to zanebot (needs an isaac-hail module sha bump + restart). Once deployed, the 6 dangling warnings clear for frontmatter bands.

Follow-up (separate): zanebot `config/hail/old-bean-work.md` / `old-bean-verify.md` use crew-tags + no session-tags — will now fail validate via :requires-any? :session/:session-tags. Delete or migrate to session-tags.
