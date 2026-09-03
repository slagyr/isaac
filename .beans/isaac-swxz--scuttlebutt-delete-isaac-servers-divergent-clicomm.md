---
# isaac-swxz
title: 'Scuttlebutt: delete isaac-server''s divergent CliComm (src/isaac/comm/cli.clj)'
status: draft
type: task
tags:
    - scuttlebutt
    - server
created_at: 2026-09-03T16:40:56Z
updated_at: 2026-09-03T16:40:56Z
blocked_by:
    - isaac-jarr
---

isaac-server main still ships src/isaac/comm/cli.clj, a divergent copy of the agent CliComm that 5nxf deleted. It implements the removed methods (on-text-chunk, on-compaction-*) and will fail to load against the new protocol; even if it loaded it would shadow/compete. Nothing else in isaac-server references isaac.comm.cli (grep confirmed 2026-09-03). Delete it, bump the agent pin to the released 5nxf SHA, full module gate green. Must land in the same train as the agent pin.
