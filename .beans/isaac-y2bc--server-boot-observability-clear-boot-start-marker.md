---
# isaac-y2bc
title: 'Server boot observability: clear boot-start marker + per-module load/activate (incl agent/server) in order'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-20T03:48:08Z
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

- Verification failed on 2026-06-19 against fetched GitHub heads `isaac-foundation` `11f03d8` and `isaac-server` `997ff9d`, not the stale local `../plan` mirrors.
- What is correct:
  - `env ISAAC_GIT=1 bb spec spec/isaac/module/loader_spec.clj spec/isaac/module/lifecycle_spec.clj` in `isaac-foundation` passed: `41 examples, 0 failures, 98 assertions`.
  - `env ISAAC_GIT=1 bb spec spec/isaac/server/app_spec.clj spec/isaac/server/cli_spec.clj` in `isaac-server` passed: `42 examples, 0 failures, 78 assertions`.
  - The follow-up changed [features/module/activation.feature](/Users/micahmartin/agents/verify/isaac-server/features/module/activation.feature:20) to split the `:module/activated` and `:telly/started` checks into separate log assertions, matching the prior review note.
  - The delivered code still contains the requested observability surfaces: `:server/boot-starting` in [src/isaac/server/cli.clj](/Users/micahmartin/agents/verify/isaac-server/src/isaac/server/cli.clj:65), phase logs and summary in [src/isaac/server/app.clj](/Users/micahmartin/agents/verify/isaac-server/src/isaac/server/app.clj:199), and `:module/loaded` plus topological `activate-modules!` in [src/isaac/module/loader.clj](/Users/micahmartin/agents/verify/isaac-foundation/src/isaac/module/loader.clj:871).
- What is wrong:
  - `env ISAAC_GIT=1 bb features features/module/activation.feature features/server/command.feature` in `isaac-server` still fails `1/6` scenarios on current head.
  - The same scenario is still red: [features/module/activation.feature](/Users/micahmartin/agents/verify/isaac-server/features/module/activation.feature:6) “Activating the telly module on first comm slot use”.
  - The split assertion did not fix the matcher behavior. The standalone `:telly/started` check still binds against an earlier one-row window before the comm start entry arrives and fails with: `Row 0: event: Expected ":telly/started", got: :server/boot-phase`.
- Net: the observability work is present, but the acceptance feature remains non-green on current head, so the bean is still not verifier-ready.

- Re-verified on 2026-06-19 after the bean was re-tagged `unverified`: `isaac-server` GitHub `main` had not advanced beyond `997ff9d`, and rerunning `env ISAAC_GIT=1 bb features features/module/activation.feature features/server/command.feature` produced the same `1/6` failure above.

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
