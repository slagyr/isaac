---
# isaac-5f0m
title: 'Handbook chapter: isaac-worksite'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:33Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-worksite` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

- Cover the concepts THIS module owns (its config tables and keys, tools, comms, commands, behaviors), written for a MODEL operating Isaac, not a developer: what it is, how to change it with `handbook__configure` (config paths), how to verify, then a `### Troubleshooting` subsection under each concept. No source-code walkthroughs.
- Ground every claim in the module's code, manifest and feature scenarios. Mark anything you can't verify with `[verify]` for Micah.
- Refer to other modules' concepts by one line plus their topic id (e.g. crews → `isaac.agent`); don't re-document them. Use foundation's chapter (`isaac-foundation/src/isaac/foundation/handbook.md`) as the pattern and vocabulary.
- Schema `:description`s: any config key this module declares without a `:description` gets one (the config reference is generated from them).

## Acceptance

- Manifest `:handbook` names the chapter; loading config raises no "handbook ... not found" warning.
- A lint spec like foundation's `spec/isaac/foundation/handbook_chapter_spec.clj` (backticked `config:<path>` refs resolve in the composed schema; `isaac <command>` refs exist).
- The repo's full CI green; landed on main with a single squash commit.

## Ungated

Documentation plus a lint spec; no behavior change. Worker hands off `tag=unverified`; Micah reviews the chapter text.

## Landed on main

main-sha: isaac-worksite 3eac930aa9bcd75b749762bcd90815c9c5e0061a

Added `src/isaac/worksite/handbook.md` (~2000 words: worksites as a
resource-pool type, validation of the retired `:worksites` key, leasing a
member and binding `:session/cwd`, operator locks vs. turn leases and
stale-lease stealing, the `isaac worksites` CLI), wired via manifest
`:handbook`, plus `spec/isaac/worksite/handbook_chapter_spec.clj` (follows
isaac-foundation's/isaac-imessage's convention: backtick `config:<path>`
refs resolve against the composed schema, `isaac <command>` mentions match
registered top-level commands; isaac-worksite is already `:builtin? true`
so no manual index-merge was needed, unlike isaac-imessage's isaac-6nfg).

No new config keys — worksite reuses isaac-agent's existing
`resource-pools.<name>.type`/`.members` schema (already described there),
so no schema `:description` additions were needed.

One correction made during grounding: dropped two backtick
`` `config:resource-pools.decks.type` ``/`` `config:resource-pools.decks.members` ``
refs from an earlier draft — they don't resolve against the *currently
pinned* isaac-foundation sha (9dd4bff8…, 34 commits behind main), whose
`isaac.config.schema.resolve/entity-collections` predates `:resource-pools`
being added to that set. Per "prefer NOT bumping," left the pins alone and
used plain `config get resource-pools.decks.<field>` prose instead — not a
functional gap, just this repo's pin being older than `main`'s entity-path
resolution.

`[verify]` item left in the chapter text for Micah: whether `isaac
worksites list --json`/`--edn` is wanted as a follow-up (today it's plain
text only).

CI: GitHub Actions green on push (`gh run watch 36678745994`, `bb ci`:
21 specs / 49 assertions + 8 features / 48 assertions, all passing).
Local verification also ran the same suite against isaac-agent's/
isaac-foundation's current `main` tips via a throwaway dev-local
worktree (not the shared checkout) — green there too once the sibling's
step-defs were pointed at the same tip as its core lib.
