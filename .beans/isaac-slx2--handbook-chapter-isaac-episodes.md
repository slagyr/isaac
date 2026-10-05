---
# isaac-slx2
title: 'Handbook chapter: isaac-episodes'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:35Z
updated_at: 2026-10-05T14:38:34Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-episodes` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

Added `resources/isaac/episodes/handbook.md` (~3,170 words) covering episodes
(open/warm/cold/close/chain, the isaac-1vx0 cold-open empty-transcript rule),
sealing scenes (idle/drift/size-cap live seal, close-time seal, `(cont)`
continuations, routine marking), recall (search, recall__search/recall__scene
tools, recall-at-open injection, floor/weights/half-life), embedding (grover/
ollama/embeddings apis), the opt-in recall ledger, and the CLI (`isaac
episodes`, `isaac recall`, `isaac embed`). Manifest's `:handbook` key added.
Every previously-undescribed key under `:episodes` and `crew.<id>.episodes`
(gist-model, ttl-minutes, seal.*, embedding.*, recall.ledger/half-life/
weights.*/inject.*) now carries a schema `:description`. Added
`spec/isaac/episodes/handbook_chapter_spec.clj` mirroring foundation's lint
(backtick `config:<path>` refs resolve; `isaac <command>` refs are
registered), adapted to read CLI contributions straight off each module's
manifest since `isaac.module.berths/module-report` isn't on the sha this
repo had pinned.

Several numeric defaults (ttl-minutes 60, seal.idle-minutes 3, seal.size-cap
80, embedding.floor-cos 0.47, recall.half-life 30, recall.weights
text/gist/lex 1.0 recency 0.5, recall.inject.full/gists 1/2) live as code
constants, not schema `:default`s — documented with
`[verify: default lives in code]` per instructions; not moved (separate
work). `:episodes :seal :drift-threshold` / `:min-tail` have no default at
all (drift sealing is off unless both are set) — noted, not flagged
`[verify]`, since that's a real absence, not an unverified guess.

**Pin bump required, not optional.** The isaac-foundation sha this repo had
pinned (`9ab25271`) predates `:handbook` manifest support entirely (loading
warned `:manifest/unknown-key :handbook`, not the "handbook not found"
warning the acceptance criteria names, but a warning either way — the
feature literally didn't exist yet at that pin). Bumping foundation forward
to `origin/main` surfaced two further mismatches only fixable by moving
together: isaac-agent's `:model-exists?` validation wasn't yet registered in
foundation's stricter lexicon check (fixed upstream by isaac-agent's
`isaac-h2oo`), and isaac-http still declared the `:comms` schema isaac-agent
now also declares, a duplicate-schema collision (fixed upstream by
isaac-http's `isaac-6pqo`, "remove the duplicate :comms schema — isaac-agent
owns it"). Bumped all three sibling pins to their `origin/main` tips
(`bb bean-gate`-style pin rule: each is reachable from that repo's own
`main`). `bb ci` (lint-cli-host + 241 specs + 98 features) green locally and
on GitHub CI after the bump.

main-sha: isaac-episodes d11fc64ebcb362dc250b62be968515b4899acb2c
