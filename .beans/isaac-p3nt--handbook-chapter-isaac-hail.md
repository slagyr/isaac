---
# isaac-p3nt
title: 'Handbook chapter: isaac-hail'
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:56:36Z
updated_at: 2026-10-05T14:38:34Z
parent: isaac-u5f5
---

Part of the "handbook chapters for every module" milestone (Micah, 2026-09-30).

## Wanted

`isaac-hail` ships a handbook chapter: a free-form markdown file named by its manifest's `:handbook` key (a classpath resource), read by crews through `handbook__read` (topic `<module-id>`, sections `<module-id>#<slug>`).

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

main-sha: isaac-hail 959a447

Handbook chapter (`src/isaac/hail/handbook.md`) + lint spec
(`spec/isaac/hail/handbook_chapter_spec.clj`) + manifest `:handbook` key
+ two backfilled schema `:description`s (`hail.<band>.cycle.checkpoint-prompt`
/ `wrap-up-prompt`).

Repinned isaac-foundation (6b95406), isaac-agent (1d26287), isaac-http
(42e302f) together to their current main tips, plus fallout fixes so
`bb ci` stays green at the new pins:

- isaac-agent's isaac-e9jl made `worker/tick!` async (claims+starts each
  runnable turn on its own thread, returns immediately). Hail's own
  `"the turn queue ticks at ..."` feature step (feature-steps/isaac/hail_handoff_steps.clj)
  now calls `worker/await-idle!` afterward, mirroring the fix isaac-agent's
  own step got.
- isaac-http's isaac-q1iu tightened auth so a *presented* bearer credential
  is always adjudicated even with no auth configured at all (previously
  served anonymous). `features/http.feature`'s Background now configures
  a legacy `http.auth.token` so its plain "Bearer secret123" scenarios
  authenticate as the intended legacy admin.

`[verify]` items left in the chapter for Micah: episodes-style default
values noted as living in code (n/a here — hail has none), the exact
merge semantics of a band's `:cycle` overlay onto the crew's own cycle
config (inferred from isaac-tic5's feature, not read from isaac-agent's
charge-construction source), and whether the band-level top-level
`:continuations` field is actually wired to a dispatched turn (grep shows
it's stored on the band registry but never read by hail's own send path).

CI: https://github.com/slagyr/isaac-hail/actions/runs/36675815553 (green).
