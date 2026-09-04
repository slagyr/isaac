---
# isaac-swxz
title: 'Scuttlebutt: delete isaac-server''s divergent CliComm (src/isaac/comm/cli.clj)'
status: in-progress
type: task
priority: normal
tags:
    - unverified
    - scuttlebutt
    - server
created_at: 2026-09-03T16:40:56Z
updated_at: 2026-09-04T02:22:28Z
blocked_by:
    - isaac-jarr
---

isaac-server main still ships src/isaac/comm/cli.clj, a divergent copy of the agent CliComm that 5nxf deleted. It implements the removed methods (on-text-chunk, on-compaction-*) and will fail to load against the new protocol; even if it loaded it would shadow/compete. Nothing else in isaac-server references isaac.comm.cli (grep confirmed 2026-09-03). Delete it, bump the agent pin to the released 5nxf SHA, full module gate green. Must land in the same train as the agent pin.



## Planning (2026-09-03)

One-time removal; no Gherkin ([[no-absence-tests]]). Acceptance is the bean's own checklist:

- [ ] `git rm src/isaac/comm/cli.clj` in isaac-server; grep confirms no remaining `isaac.comm.cli` / `->CliComm` references (2026-09-03 grep: none outside the file itself).
- [ ] deps.edn + bb.edn agent pin = the SHA isaac-jarr releases (the server must compile against the new `isaac.comm.protocol`).
- [ ] `bb features` and `bb spec` green (exit codes, not tails).
- [ ] Release the server manifest; note the SHA on this bean so the scuttlebutt train pins agent + server + discord + acp + imessage together.



## Pin target updated (2026-09-03, planner)

Pin the agent at **`bf4323326c150bdcda4be2c0245cf2f7b0cbd629`** (Release 0.1.43) — not jarr's 0b52823. Main advanced past jarr with isaac-vuto (token accounting) and isaac-q34y (idle sealing), both verified and merged; the full gate on merged main is 756/0 features, 1612/0 spec. Same protocol as 0.1.42 for this bean's purposes.

## Work observations (2026-09-04, scrapper)

Tried the bean exactly as written on `isaac-server` main `319e04f`:

- updated the agent/telly test pin sites to `bf4323326c150bdcda4be2c0245cf2f7b0cbd629`
  - `deps.edn`
  - `bb.edn`
  - `spec/isaac/configurator_steps.clj`
  - fixture SHAs in `features/module/activation.feature` and `features/module/comm_extension.feature`
- bumped `resources/isaac-manifest.edn` version `0.1.10 -> 0.1.11`
- `git rm src/isaac/comm/cli.clj`

Results:

- `bb spec` passed (`214 examples, 0 failures`).
- Baseline `bb features` on unmodified current `isaac-server` main is green here (`67 examples, 0 failures`).
- After the required agent pin bump, `bb features` fails hard before any CliComm absence matters.

Root cause found:

- `isaac-server` still carries **other** divergent comm copies besides `src/isaac/comm/cli.clj`:
  - `src/isaac/comm/protocol.clj`
  - `src/isaac/comm/null.clj`
  - `src/isaac/comm/memory.clj`
- Those files are older than the agent's scuttlebutt protocol. In particular, server `isaac.comm.protocol` has no `comm/defaults`, while the pinned `isaac.comm.telly` module from agent `bf43233` extends `Comm` via `(merge comm/defaults ...)`, so feature boot fails with:
  - `Unable to resolve symbol: comm/defaults`
- The same pin also pulls later agent code that expects newer session-store APIs (`session-store/chronicle-transcript`), so module activation fails during feature boot as well.

Conflict:

This bean's scoped change set (delete only `src/isaac/comm/cli.clj` + bump the agent pin) is insufficient to satisfy its own acceptance on current `isaac-server` main. To make the pin target green, more than the deleted CliComm must change in `isaac-server` — at minimum the server-owned comm copies must be reconciled with the agent scuttlebutt protocol, and possibly additional server/agent alignment work is needed for the newer agent APIs now exercised in feature boot.

Planner decision needed: either broaden `isaac-swxz` to include reconciling the remaining server comm copies / compatibility fallout, or retarget the required agent pin to a SHA compatible with current `isaac-server` main.


## Planner adjustment (2026-09-04, prowl@isaac-plan) — choose A: broaden to the compatibility work required by the scuttlebutt pin

Decision: **A**. Do **not** retarget this bean to an older/softer agent pin. Keep the required agent pin at **`bf4323326c150bdcda4be2c0245cf2f7b0cbd629`**.

Why B is rejected:
- This bean exists because `isaac-server` still ships a divergent in-tree `src/isaac/comm/cli.clj` after the scuttlebutt protocol replacement. Pinning away from the scuttlebutt train would hide the break instead of reconciling it.
- The earlier jarr train SHA `0b528236985c6e67c63193e65cafb74a73d6d5bc` does **not** rescue this. The agent's `isaac.comm.protocol/defaults` and the telly module's `(merge comm/defaults ...)` shape already exist there too, and `session-store/chronicle-transcript` is already on that train. So the failure is not unique to `bf43233`; it is a real compatibility gap in `isaac-server`.

Therefore this bean is broadened from "delete CliComm only" to **"make isaac-server green on the required scuttlebutt train, including reconciling its remaining divergent comm/session compatibility surface"**.

### In scope now

1. Delete `src/isaac/comm/cli.clj` as originally planned.
2. Reconcile the other server-owned divergent comm copies that block the pinned train:
   - `src/isaac/comm/protocol.clj`
   - `src/isaac/comm/null.clj`
   - `src/isaac/comm/memory.clj`
3. Reconcile any additional `isaac-server` compatibility fallout that is **directly required** to boot and run features with agent `bf43233`, including the newer session-store API expectations surfaced during module activation (for example `chronicle-transcript`).

### Not in scope

- Do **not** pin backward to avoid the train.
- Do **not** absorb unrelated product work outside the compatibility needed for the bumped pin. If, after the comm/session alignment above, new failures remain that are not caused by the scuttlebutt train compatibility gap, stop and hail plan with the exact failing symbols/files.

### Acceptance (supersedes the narrower checklist above)

- agent/telly pin sites in `isaac-server` are `bf4323326c150bdcda4be2c0245cf2f7b0cbd629`
- `src/isaac/comm/cli.clj` is gone
- `isaac-server` no longer ships a comm surface that is incompatible with the pinned agent/telly train; module feature boot must not fail on `comm/defaults` resolution or on the newer session-store API expected by the pinned agent
- `bb features` green (exit code, not tail)
- `bb spec` green
- release manifest noted on this bean with the server SHA that rides the train

Implementation freedom is the worker's call: remove the divergent server copies in favor of the agent surface, or update the server-owned copies so they match the pinned protocol/API surface. The contract is compatibility on the required train, not a specific code move.



## Work complete (2026-09-04, scrapper@isaac-work-2)

Completed in `isaac-server` on branch `bean/isaac-swxz` from `origin/main@319e04f15b203537b99cfa47c9624ede90a43bf1`.

Implemented compatibility required by the pinned scuttlebutt train:
- pinned `deps.edn`, `bb.edn`, feature fixtures, and configurator steps to agent/telly SHA `bf4323326c150bdcda4be2c0245cf2f7b0cbd629`
- deleted `src/isaac/comm/cli.clj`
- reconciled server comm copies in `src/isaac/comm/protocol.clj`, `src/isaac/comm/null.clj`, and `src/isaac/comm/memory.clj` to the newer protocol surface
- added `src/isaac/comm/render.clj` required by the aligned comm implementation
- expanded `src/isaac/session/store/spi.clj` to the newer session-store API expected during module activation (`chronicle-transcript`, reckoning, turn-marker, rename support)
- added explicit `io.github.slagyr/isaac-agent` classpath dependency so agent store impl namespaces resolve during feature/spec boot
- fixed the hot-reload logging feature fixture so it edits valid config (`server.host`) on the pinned train
- bumped server manifest version to `0.1.11`

Verification on this branch:
- `bb features` -> `67 examples, 0 failures, 184 assertions`
- `bb spec` -> `214 examples, 0 failures, 414 assertions`

Train/server implementation SHA: `1207d456c99a2c7044e46fd0fc6a64ceb567448d`.
