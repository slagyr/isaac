---
# isaac-jarr
title: 'Scuttlebutt train: merge isaac-5nxf onto isaac-agent main and release'
status: in-progress
type: task
priority: normal
tags:
    - scuttlebutt
created_at: 2026-09-03T16:32:28Z
updated_at: 2026-09-03T18:49:44Z
parent: isaac-5nxf
---

Integration only. bean/isaac-5nxf is verified (completed) but never merged; main has since taken x2up, 0oqd, and 7dkp. Merge (or rebase) it onto main, resolve the two spec conflicts (spec/isaac/comm/delivery/worker_spec.clj: keep the :turn.queue/tick scheduler expectation from 0oqd; spec/isaac/comm_spec.clj: PromptComm 2-arity from 0oqd against the new protocol), confirm cli.clj is gone, full gate (bb features AND bb spec, exit codes not tails), release manifest + CHANGELOG. Do NOT pin the agent in modules.edn until the four module beans that ride this train (discord/acp/imessage mechanical migrations + server CliComm deletion) are green against the released SHA — the module deftypes implement removed methods and fail to load against the new protocol. Acceptance: isaac-agent main contains d80854a + 1ab44ab (or their rebased equivalents); bb features and bb spec green; features/comm/scuttlebutt.feature un-@wip and green.


## Verify fail (attempt 1, 2026-09-03): full acceptance gate is still red (`bb features` does not complete green on the accepted main SHA)

Evidence on `isaac-agent` `origin/main` `082f9b0`:
- required train content is present:
  - `git merge-base --is-ancestor d80854a HEAD` → success
  - `git merge-base --is-ancestor 1ab44ab HEAD` → success
  - `src/isaac/comm/cli.clj` is absent
  - `features/comm/scuttlebutt.feature` is not `@wip`
- targeted scuttlebutt acceptance is green:
  - `bb features features/comm/scuttlebutt.feature` → `4 examples, 0 failures, 6 assertions`
- spec gate is green:
  - `bb spec` → `1604 examples, 0 failures, 3294 assertions, 3 pending`
- but the required full feature gate is not green:
  - `bb features` exits `124`
  - terminal output ends with `features timed out after 180s`
  - the run emitted `F` markers before timing out, so the full gate did not complete cleanly

This bean explicitly requires the full gate by exit code (`bb features AND bb spec, exit codes not tails`). It cannot pass until `bb features` exits 0 on the accepted main SHA, or planning narrows/amends the gate.
