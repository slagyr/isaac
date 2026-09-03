---
# isaac-jarr
title: 'Scuttlebutt train: merge isaac-5nxf onto isaac-agent main and release'
status: todo
type: task
tags:
    - scuttlebutt
created_at: 2026-09-03T16:32:28Z
updated_at: 2026-09-03T16:32:28Z
parent: isaac-5nxf
---

Integration only. bean/isaac-5nxf is verified (completed) but never merged; main has since taken x2up, 0oqd, and 7dkp. Merge (or rebase) it onto main, resolve the two spec conflicts (spec/isaac/comm/delivery/worker_spec.clj: keep the :turn.queue/tick scheduler expectation from 0oqd; spec/isaac/comm_spec.clj: PromptComm 2-arity from 0oqd against the new protocol), confirm cli.clj is gone, full gate (bb features AND bb spec, exit codes not tails), release manifest + CHANGELOG. Do NOT pin the agent in modules.edn until the four module beans that ride this train (discord/acp/imessage mechanical migrations + server CliComm deletion) are green against the released SHA — the module deftypes implement removed methods and fail to load against the new protocol. Acceptance: isaac-agent main contains d80854a + 1ab44ab (or their rebased equivalents); bb features and bb spec green; features/comm/scuttlebutt.feature un-@wip and green.
