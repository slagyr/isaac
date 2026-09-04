---
# isaac-swxz
title: 'Scuttlebutt: delete isaac-server''s divergent CliComm (src/isaac/comm/cli.clj)'
status: in-progress
type: task
priority: normal
tags:
    - scuttlebutt
    - server
created_at: 2026-09-03T16:40:56Z
updated_at: 2026-09-04T00:56:06Z
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
