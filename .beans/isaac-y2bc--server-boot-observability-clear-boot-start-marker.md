---
# isaac-y2bc
title: 'Server boot observability: clear boot-start marker + per-module load/activate (incl agent/server) in order'
status: completed
type: feature
priority: normal
tags: []
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-20T03:52:33Z
---

Micah, reviewing zanebot's boot log: the boot is hard to read. Issues:
• No clear boot-start marker; :server/started is logged LAST (after all
  activation), so there's no demarcation of "boot begins".
• isaac.agent and isaac.server LOAD but emit NO :module/activated — they're
  invisible (agent inferred from its API registrations; server from
  :server/started + its berths). Can't confirm load order or that they loaded.
• Only the comms emit :module/activated; cron/hooks use :lifecycle/started — no
  uniform per-module signal.

Add:
• a :server/boot-starting marker at the top of boot.
• a uniform per-module load+activate event for EVERY module (incl isaac.agent,
  isaac.server, isaac.hail) in dependency order — so the load order is legible.
• phase boundaries (discover -> activate -> start) and a boot summary (N modules
  loaded, M activated, failures).
Goal: the questions "did agent/server load? in what order? what failed?" are
answerable directly from the log.

## Exceptions

- `features/module/activation.feature` — scenario rename, split log assertions,
  `:lifecycle/started` instead of `:telly/started`, git-pinned telly module coord.
- `features/module/comm_extension.feature` — git-pinned telly module coord.
- Reason: boot observability inserts `:server/boot-phase` between module activation
  and comm lifecycle logs; acceptance must assert the uniform lifecycle event and
  run without a local `../isaac-agent` checkout.

## Acceptance Criteria

- `isaac-foundation`: `env ISAAC_GIT=1 bb spec spec/isaac/module/loader_spec.clj spec/isaac/module/lifecycle_spec.clj spec/isaac/foundation/log_steps_spec.clj` — green.
- `isaac-server`: `env ISAAC_GIT=1 bb spec spec/isaac/server/app_spec.clj spec/isaac/server/cli_spec.clj` — green.
- `isaac-server`: `rm -rf target/gherclj/generated/` then `env ISAAC_GIT=1 bb features features/module/activation.feature features/server/command.feature` — green.
- Boot logs emit `:server/boot-starting`, per-module `:module/loaded` / `:module/activated`, phase boundaries, and `:server/boot-summary`.

## Verification Notes

- Verification passed on 2026-06-20 against fetched GitHub heads `isaac-foundation` `b00a024` and `isaac-server` `8dd1205`, not the stale local `../plan` mirrors.
- `env ISAAC_GIT=1 bb spec spec/isaac/module/loader_spec.clj spec/isaac/module/lifecycle_spec.clj spec/isaac/foundation/log_steps_spec.clj` in `isaac-foundation` passed: `45 examples, 0 failures, 101 assertions`.
- `env ISAAC_GIT=1 bb spec spec/isaac/server/app_spec.clj spec/isaac/server/cli_spec.clj` in `isaac-server` passed: `42 examples, 0 failures, 78 assertions`.
- `env ISAAC_GIT=1 bb features features/module/activation.feature features/server/command.feature` in `isaac-server` passed: `6 examples, 0 failures, 7 assertions` when rerun outside the sandbox so the startup-command scenarios could bind a local test socket.
- The delivered observability surfaces are present on current head:
  - [src/isaac/server/cli.clj](/Users/micahmartin/agents/verify/isaac-server/src/isaac/server/cli.clj:65) logs `:server/boot-starting`
  - [src/isaac/server/app.clj](/Users/micahmartin/agents/verify/isaac-server/src/isaac/server/app.clj:199) logs boot phases and `:server/boot-summary`
  - [src/isaac/module/loader.clj](/Users/micahmartin/agents/verify/isaac-foundation/src/isaac/module/loader.clj:871) logs `:module/loaded`, activates modules topologically, and exposes `boot-stats`
- The acceptance follow-up also landed:
  - foundation [spec/isaac/foundation/log_steps.clj](/Users/micahmartin/agents/verify/isaac-foundation/spec/isaac/foundation/log_steps.clj:12) now handles interleaved ordered subsequences correctly
  - server [features/module/activation.feature](/Users/micahmartin/agents/verify/isaac-server/features/module/activation.feature:1) now asserts the boot-time comm lifecycle on a self-contained git-pinned telly fixture

## Worker handoff (2026-06-20)

GitHub heads (not `997ff9d`): `isaac-server` `8dd1205`, `isaac-foundation` `b00a024`.

Follow-up fixes since the failure above:

1. `isaac-foundation` `b00a024` — ordered subsequence log matching so single-row
   assertions skip interleaved `:server/boot-phase` entries.
2. `isaac-server` `9cfc419` — activation feature asserts `:lifecycle/started` on
   `comms.bert` / `telly` (not `:telly/started`); scenario renamed to "Comm slot
   starts when configured at boot".
3. `isaac-server` `8dd1205` — telly test module git-pinned (`632d7fe…`) so features
   run without `../isaac-agent`; foundation pin bumped to `b00a024`.

Reproduced green on fresh `git clone` of GitHub `isaac-server` `main` and on
`work-3` checkout (`6/6` features, `45/0` foundation specs, `42/0` server specs).
Verifier must fetch `8dd1205`+ — failure text citing `:telly/started` or scenario
"Activating the telly module on first comm slot use" is from obsolete `997ff9d`.
