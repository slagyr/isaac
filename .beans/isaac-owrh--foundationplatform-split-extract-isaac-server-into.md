---
# isaac-owrh
title: 'Foundation/platform split: extract isaac-server into its own module (phase 9)'
status: todo
type: task
priority: normal
created_at: 2026-06-05T06:33:32Z
updated_at: 2026-06-05T06:33:42Z
parent: isaac-brth
blocked_by:
    - isaac-qpgy
    - isaac-8v1n
    - isaac-w7o5
    - isaac-ho18
    - isaac-qqgv
---

Phase 9 of isaac-brth. With every extension kind now a berth (after
phases 4-8), extracting `isaac-server` from the core `isaac` repo
is a packaging exercise. The foundation shrinks to the smallest set
of namespaces every module needs reachable; everything else moves
into the `isaac-server` module repo.

## Foundation (the `isaac` repo after the split)

Keeps:

- `isaac.cli` / `isaac.main` (CLI dispatcher)
- `isaac.module` (loader, manifest, registry)
- `isaac.system` / `isaac.nexus`
- `isaac.fs`
- `isaac.logger`
- Schema runtime + isaac lexicon extensions (isaac-c2g5, isaac-2yqb)
- `isaac.scheduler` (primitives only — instance creation by isaac-server)
- `isaac.root`
- Module protocol
- Berth-processing pipeline (isaac-jr64, isaac-8yxs)
- The Module / Node protocol definitions

Foundation manifest declares ONE built-in berth: `:cli` (per
isaac-qpgy). Plus `isaac init` as the only built-in command (also
per isaac-qpgy).

## isaac-server (new module repo)

Everything else from today's `src/isaac/`:

- HTTP server (`isaac.server.app`, httpkit wiring)
- Session store (`isaac.session`)
- Bridge / charge / turn loop (`isaac.bridge`, `isaac.charge`,
  `isaac.drive`)
- Prompt catalog (`isaac.prompt`)
- Hail router + delivery worker (`isaac.hail`)
- Cron (`isaac.cron`)
- Crew management, soul handling
- Tool registry + built-in tools (`isaac.tool.*`)
- Slash registry + built-in commands (`isaac.slash.*`)
- Hook engine (`isaac.hooks`)
- LLM api dispatch + provider implementations (`isaac.llm.*`)
- The seven berth DECLARATIONS:
  `:isaac.server/comm`, `:isaac.server/tools`, `:isaac.server/provider`,
  `:isaac.server/llm-api`, `:isaac.server/slash-commands`,
  `:isaac.server/hook`, `:isaac.server/route`.

isaac-server's manifest contributes those berths in `:berths` and
contributes built-ins (built-in tools, built-in slash commands,
built-in hooks, the hail `:post /hail/send` route) as
manifest entries.

## Consumer modules to update

isaac-discord, isaac-imessage, isaac-acp:

- `:deps {:isaac.server {…}}` instead of `:deps {:isaac {…}}` (the
  foundation alone doesn't host the comm berth they contribute to).
- No source code changes — their contributions already target
  `:isaac.server/comm` (post phase 8).

## Feature

`features/module/foundation_split.feature` — one `@wip` scenario
proving the foundation alone has no platform commands. The
inverse (foundation + isaac-server brings everything back) is
implicitly covered by every existing scenario in the suite —
they all assume the platform is live.

## Acceptance

- Remove `@wip` from `features/module/foundation_split.feature`.
- `bb features features/module/foundation_split.feature` passes.
- `bb features` and `bb spec` green for the WHOLE suite (across
  the foundation and isaac-server repos) — no regression.
- Greps:
  - In the foundation repo: `rg 'isaac.bridge|isaac.session|isaac.hail|isaac.cron|isaac.llm|isaac.server|isaac.slash|isaac.tool.builtin' src/` returns zero hits.
  - In isaac-server: the seven berth declarations are present in
    `src/isaac-manifest.edn` `:berths`.
- isaac-discord / isaac-imessage / isaac-acp test suites pass with
  their `:deps` pointing at the new isaac-server module.

## Out of scope

- Slim CLI binary packaging (phase 10).
- Further extraction (LLM provider modules, hail-as-module,
  cron-as-module). Enabled by phase 9 but those are their own
  follow-up beans IF/WHEN someone wants them.
- New module discovery UX (`isaac modules install`). Per the brth
  bean's phase 2, that's its own design exercise.

## Dependencies

- isaac-qpgy (phase 4 — `:cli` as berth; foundation needs a way to
  host its remaining built-in command without static-require).
- isaac-8v1n (phase 5 — `:route` migrated; hail's route entry
  flows through the berth mechanism so it can move with
  isaac-server).
- isaac-w7o5 (phase 6 — `:tools` migrated).
- isaac-ho18 (phase 7 — `:provider`, `:llm/api`, `:slash-commands`,
  `:hook` migrated).
- isaac-qqgv (phase 8 — `:comm` migrated; discord/imessage/acp
  contributions use the berth shape).

Effectively blocked by ALL the phase-5-8 migrations. Phase 9 can't
sensibly land until every extension kind has been moved off the
hardcoded top-level manifest position.

## Notes for the worker

- This is the FIRST cross-repo restructure in isaac. Plan the cut
  on both repos simultaneously; both PRs land in coordinated
  fashion. A half-done state (isaac repo split but isaac-server
  not yet a module dep elsewhere) breaks dependent module repos.
- The foundation `init` command (per isaac-qpgy) should be enough
  to bootstrap a fresh `~/.isaac/` with a sensible default
  `isaac.edn` that includes `:modules {:isaac.server …}` — so users
  who run `brew install isaac` and then `isaac init` get the full
  experience without having to know about the split.
- Decide during impl whether the brew formula for `isaac` continues
  to include isaac-server as a default dep (zero-config setup) or
  requires explicit opt-in (true "thin foundation"). The brth bean
  acknowledges this depends on the size-measurement work
  (phase 3 — still open).
