---
# isaac-o9np
title: Hail bands missing :frontmatter? — <name>.md with frontmatter doesn't load (and warns dangling)
status: draft
type: bug
priority: normal
created_at: 2026-06-25T17:10:34Z
updated_at: 2026-06-25T17:12:43Z
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

## ALREADY IN FLIGHT (observed 2026-06-25)
The fix is being implemented concurrently by another agent — UNCOMMITTED WIP in the local isaac-hail checkout:
- `src/isaac-manifest.edn`: `+   :frontmatter? true,` added to the hail schema descriptor (exactly the diagnosed fix).
- `features/bands.feature`: description updated to '(or single <name>.md with YAML frontmatter + prompt body, like crews)'.
So: do NOT write scenarios here or touch the hail repo — it would collide with that WIP. This bean documents the bug; let the in-flight work complete it (and add its own bands.feature scenarios). Scenario drafting paused for this reason.
