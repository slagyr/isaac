---
# isaac-y2bc
title: 'Server boot observability: clear boot-start marker + per-module load/activate (incl agent/server) in order'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-19T23:12:59Z
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

## Verification Notes

- Verification failed on 2026-06-19 against fetched GitHub heads `isaac-foundation` `11f03d8` and `isaac-server` `be9ac82`, not the stale local `../plan` mirrors.
- What is correct:
  - `env ISAAC_GIT=1 bb spec spec/isaac/module/loader_spec.clj spec/isaac/module/lifecycle_spec.clj` in `isaac-foundation` passed: `41 examples, 0 failures, 98 assertions`.
  - `env ISAAC_GIT=1 bb spec spec/isaac/server/app_spec.clj spec/isaac/server/cli_spec.clj` in `isaac-server` passed: `42 examples, 0 failures, 78 assertions`.
  - `env ISAAC_GIT=1 bb features features/server/command.feature` in `isaac-server` passed: `3 examples, 0 failures, 3 assertions`.
  - The delivered code does add the requested surfaces: `:server/boot-starting` in [src/isaac/server/cli.clj](/Users/micahmartin/agents/verify/isaac-server/src/isaac/server/cli.clj:65), phase logs and summary in [src/isaac/server/app.clj](/Users/micahmartin/agents/verify/isaac-server/src/isaac/server/app.clj:199), and `:module/loaded` plus topological `activate-modules!` in [src/isaac/module/loader.clj](/Users/micahmartin/agents/verify/isaac-foundation/src/isaac/module/loader.clj:871).
- What is wrong:
  - `env ISAAC_GIT=1 bb features features/module/activation.feature features/server/command.feature` in `isaac-server` fails `1/6` scenarios.
  - The failing scenario is [features/module/activation.feature](/Users/micahmartin/agents/verify/isaac-server/features/module/activation.feature:6) “Activating the telly module on first comm slot use”.
  - That scenario still asserts the ordered subsequence `:module/activated isaac.comm.telly` immediately followed by `:telly/started bert`, but the new boot observability inserts `:server/boot-phase` between them. Current failure text: `Row 1: event: Expected ":telly/started", got: :server/boot-phase`.
- Net: the observability work is largely present, but the bean is not verifier-green yet because an affected acceptance feature on current head still fails.
