---
# isaac-69d9
title: Retire the :frontmatter?/:companion berth — the _ sentinel replaces it
status: todo
type: feature
priority: normal
created_at: 2026-09-23T19:41:14Z
updated_at: 2026-09-23T19:41:14Z
---

Repo: **isaac-foundation** (`config/entities.clj`, `companions.clj`, `mutate.clj`,
`src/isaac-manifest.edn`, `features/cli/init.feature`).

Follows isaac-49zp, which landed the `_` body sentinel
(`main-sha isaac-foundation 7b2f053`).

## Why

isaac-49zp made a markdown file declare which key its body fills: a frontmatter
field valued `_` takes the body. That is strictly more general than the berth
mechanism it sits beside — `:frontmatter?` and `:companion {:field … :mode …}`,
declared per kind in a manifest. With the sentinel, the **file** says where its
body goes, so nothing has to declare it.

49zp already stopped foundation hardcoding kind names (`companion-md-specs`'
`{:crew → :soul, :berths → :ledger}` now derives from the owning module's
descriptor). This finishes the job by removing the declaration entirely.

Micah, 2026-09-23: "this new story kind of makes the existing berth for the
front matter and the companion unnecessary… they are compatible for the most
part."

## Change

- Retire `:frontmatter?` and `:companion` from the berth schema
  (`src/isaac-manifest.edn`) and from the loader. Any `.md` under `config/` with
  frontmatter is an entity; the body goes to whichever field is valued `_`.
- `:mode :exclusive` disappears with them and needs no replacement: a field
  either carries a real value or `_`, never both, so there is nothing left to
  make exclusive.
- `init` scaffolds the sentinel — `soul: _` in `crew/skipper.md`, `prompt: _`
  in `cron/heartbeat.md`.

## A sentinel-less .md is a load error, not a silent drop

**This is the safety rail and the point of the bean.** If the implicit path is
deleted and a `.md` carries frontmatter but no `_`-valued field, its body simply
vanishes — a crew loses its soul, a cron job loses its prompt, and nothing says
so. That is exactly the failure mode isaac-nq4c exists to prevent, and the same
class as the silent drops isaac-49zp was written to end.

So: frontmatter present, no field valued `_`, non-empty body → **load error**
naming the file and the fields it could have filled. An upgrade then fails
loudly, once, and the operator adds the lines.

## Migration

Every live `.md` config file needs one line. On zanebot that is roughly 13 crew
souls, ~10 hail band files, plus cron and hooks — order 25-30 files. A second
host (Yopp) carries its own set.

`features/cli/init.feature` is **frozen** and scaffolds both files without
sentinels (`crew/skipper.md` with `model: "llama"`, `cron/heartbeat.md` with
`expr`/`crew`). It needs a planner re-baseline in the same pass — planner's job,
not the worker's.

## Explicitly out of scope: hail bands

`isaac-work.md`'s body becomes the prompt through hail's own
`apply-to-load-result!`, not through `:companion`. Retiring the companion berth
does **not** migrate bands, and they must not be assumed to come along. That is
the separate hail migration.

## Acceptance

- A `.md` with `<field>: _` in frontmatter fills that field from the body, for a
  kind with no `:companion` declaration of any sort.
- `:frontmatter?` and `:companion` are gone from the berth schema; a manifest
  still declaring them is not honoured.
- A `.md` with frontmatter, no `_`-valued field and a non-empty body fails the
  load with an error naming the file.
- `isaac init` scaffolds `soul: _` and `prompt: _`; `init.feature` re-baselined
  to match.
- Existing `.md` files that carry a sentinel keep working unchanged.
- Spec coverage for the sentinel on an undeclared kind, and for the
  sentinel-less error.
