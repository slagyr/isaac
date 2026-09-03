---
# isaac-swxz
title: 'Scuttlebutt: delete isaac-server''s divergent CliComm (src/isaac/comm/cli.clj)'
status: todo
type: task
priority: normal
tags:
    - scuttlebutt
    - server
created_at: 2026-09-03T16:40:56Z
updated_at: 2026-09-03T17:57:54Z
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
