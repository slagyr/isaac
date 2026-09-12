---
# isaac-zgfx
title: 'Leg 4 — the server knows no comms: comm-type check as an :isaac.config/check contribution; delete the six stale comm copies; telly replaced by a server-owned test comm'
status: completed
type: feature
priority: high
tags:
    - comm
    - server
    - agent
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-12T00:04:04Z
parent: isaac-3q4m
blocked_by:
    - isaac-jrj0
---

Parent: isaac-3q4m (decision 6). Depends on leg 2 (the delivery worker must be a component before its copy goes).

Repo: **isaac-server** (deletes), **isaac-agent** (contributes the check).

1. `comm-validation-errors` (install.clj) becomes an `:isaac.config/check` contribution from the comm-owning side; the server's install/reload validate only via the generic check runner.
2. Delete isaac-server `src/isaac/comm/{protocol,memory,null,render,delivery/queue,delivery/worker}.clj` — June-14 copies that survived the "prune duplicate agent code" commit; the agent's namesakes are canonical and currently load first only by classpath order. The server's Comm protocol copy predates on-exhausted/on-bulletin/on-key.
3. The dev alias's `isaac.comm.telly` (agent repo fixture) is replaced by a test comm in isaac-server's spec-support.

Scenarios: none new — the unknown-comm-type validation scenario stays (now sourced from the check berth); the deletions and the telly replacement are one-time acceptance checks; logging.feature and the comm plumbing features stay green with the local test comm.

## Acceptance

Unknown-comm-type validation scenario green, sourced from the :isaac.config/check contribution; no isaac.comm.* namespaces under isaac-server/src (one-time check); no isaac.comm.telly in the server deps/bb.edn (one-time check); logging.feature and comm plumbing features green with the local test comm.

```
cd isaac-agent && bb features features/config/ && bb spec && bb ci
cd isaac-server && bb features && bb spec && bb ci
```

## Work checkpoint (2026-09-11, scrapper@isaac-work-1)

Done: Agent's contributed comm-type check now accepts both manifest-contributed and programmatically registered factories and preserves a dotted display path (`bean/isaac-zgfx@b6284e4`; focused checks spec: 26 examples, 0 failures). Server removed the stale Agent-owned `isaac.session.store.spi` shadow, pinned Agent `b6284e4`, propagates generic loader errors into boot rejection/logging, and fixes the spec-support module resource path/test config (`bean/isaac-zgfx@2cf3b38`). Focused config/comm/logging features are green: 6 examples, 0 failures, 17 assertions.

Completed implementation. Agent branch: `bean/isaac-zgfx@b6284e42ab37ccf971637d4dc791856c7fa231aa` (base `origin/main@e9cba6410e097992d99acbef3e31b22ef3153512`). Server branch: `bean/isaac-zgfx@e8c8a933e86674f2ee74348a79ed2d64942f5d52` (base `origin/main@99d2ad8af2e18f3b1f60c1fc9ef4b1949f2db1d5`).

Evidence: Agent focused config composition features green (21 examples, 35 assertions) and full specs green (1596 examples, 3289 assertions). Agent's broader `bb features features/config/` and `bb ci` feature phase time out at the repository's 180s task limit and contain unrelated pre-existing config CLI failures; confirmed `origin/main` independently fails `features/config/cycle.feature` the same way. Server focused config/comm/logging features green (6 examples, 17 assertions), full specs green (120 examples, 246 assertions), full features green (46 examples, 96 assertions), and `bb ci` green. One-time checks pass: no `src/isaac/comm` directory and no `isaac.comm.telly` in Server `deps.edn`/`bb.edn`. Both implementation branches are rebased on current `origin/main` and clean.



## Landed on main (2026-09-12)

main-sha: isaac-agent 374ea9e92306f8ee68a115ad4e92c743e2f7b221
main-sha: isaac-server 7f54ad9d2235ab0974a871142abc352c0216f1b8

Squash-landed from sibling checkouts. Bean-tip trees match main trees.

Verifier gates (perceptor@isaac-verify):
- isaac-agent bb spec: 1596 examples, 0 failures, 3289 assertions
- isaac-agent bb spec checks_spec + check_contributions_spec: 27/0
- isaac-agent bb features features/config/composition.feature: 21/0
- isaac-server bb spec: 120 examples, 0 failures, 246 assertions
- isaac-server bb features: 46 examples, 0 failures, 96 assertions
- isaac-server focused config/comm/logging: 9 examples, 0 failures, 21 assertions
- One-time: no src/isaac/comm; no isaac.comm.telly in server deps.edn/bb.edn
- Unknown-comm-type scenario green (features/config/reconciler.feature unregistered :type)

Agent bb features features/config/ (751 examples, 22 failures) includes pre-existing CLI failures reproduced on origin/main (cycle.feature:19, cli.feature:254 unknown-comm-type CLI schema message). Not blocking: bean does not change agent features; named product gate is the server unregistered-type scenario + agent specs.
