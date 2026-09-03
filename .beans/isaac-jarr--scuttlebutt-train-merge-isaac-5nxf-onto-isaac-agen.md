---
# isaac-jarr
title: 'Scuttlebutt train: merge isaac-5nxf onto isaac-agent main and release'
status: in-progress
type: task
priority: normal
tags:
    - scuttlebutt
created_at: 2026-09-03T16:32:28Z
updated_at: 2026-09-03T20:27:53Z
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

## Verify fail attempt 1 (2026-09-03, scrapper@isaac-work-1)

Conflict: named full gate `bb features` cannot exit 0 on accepted main.

Train content is present on origin/main `c2b7b9e` (verifier saw `082f9b0`;
same train ancestors `d80854a` + `1ab44ab`; CliComm gone; scuttlebutt un-@wip).
`bb spec` is green (1604/0). Targeted `bb features features/comm/scuttlebutt.feature`
is green in isolation (4/0/6).

Unwrapped `bb features` (no 180s wrapper) on `c2b7b9e` finished in **228s** with
**747 examples, 5 failures, 1968 assertions**. The 180s timeout is a consequence
of the reds, not the cause. Failures:

1. `session/context_window_guard.feature:74` — bulletin `compaction/disabled`
   (5nxf migration of the old `compaction-disabled` event). Isolated scuttlebutt
   is green; this migrated scenario is not.
2. `session/compaction_memory_flush.feature:42` — compaction-turn `memory__write`
   did not persist. Feature **unchanged since 13da406** (pre-5nxf). Likely
   5nxf/jarr product interaction, not a planned 5nxf scenario.
3. `comm/scuttlebutt.feature:91` — streaming tool-progress through the ctx seam.
   Isolated `bb features features/comm/scuttlebutt.feature` is 4/0/6; fails only
   in the full suite (order/leak).
4. `bridge/cancel_aborts_work.feature:27` — turn result expected `"cancelled"`,
   got nil. Feature **unchanged since 13da406**.
5. `session/compaction_template.feature:49` — `config/compaction.md` did not
   replace the built-in template (got the default nine-section prompt). Feature
   **unchanged since 13da406**.

Making `bb features` exit 0 means either (a) fixing these five as extra 5nxf/jarr
product work beyond the merge+release scope, or (b) planner-amending acceptance
to the targeted scuttlebutt + spec gates already green, treating the five as
follow-up (several are pre-5nxf contracts that the train now trips).

Do not reopen 5nxf protocol work. Request: amend or authorize the five as
in-scope repairs, then re-dispatch.



## Planner ruling (2026-09-03, plan@micah) — the five are IN SCOPE; fix them, do not amend the gate

Baseline: agent main at 2b18dca / 13da406 (pre-merge) ran `bb features` **738/0/1964** and `bb spec` 1605/0 on 2026-09-03 (planner gate before the 0.1.41 train). Every one of the five reds is therefore a regression or leak introduced by the 5nxf merge (85bfb85..c2b7b9e), not pre-existing debt — "feature unchanged since 13da406" cuts the other way: unchanged contract, changed product. The full-suite exit code is the acceptance; it stays.

Per item:
1. `context_window_guard.feature:74` — 5nxf's own migrated row (`compaction/disabled` bulletin). Fix the migration.
2. `compaction_memory_flush.feature:42` — compaction-turn `memory__write` lost. Find what the merge changed on the compaction turn's tool path (likely the comm/cycle plumbing around the compaction call). Product fix.
3. `scuttlebutt.feature:91` — green isolated, red in suite = state leak (ctx `:progress!` seam or the mock streaming tool registered by a prior feature). Fix the leak in the fixture/registration, not by reordering.
4. `cancel_aborts_work.feature:27` — known cancel-race flake family (isaac-zcb9 / isaac-x27m / isaac-2bni). Rerun it twice in isolation; if green, record the runs and treat as flake (no fix in this bean); if red in isolation, it is a regression — fix.
5. `compaction_template.feature:49` — `config/compaction.md` override ignored. Green before the merge; find the resolution path the merge clobbered (compaction.clj / config read). Product fix.

Rules: do not reopen protocol design (5nxf is settled); unwrapped `bb features` must exit 0; `bb spec` stays green; the SHA the four module beans pin is the FINAL green main SHA — note it on this bean when you re-tag. The 0.1.42 manifest/CHANGELOG on main stands; add the repairs to its entry. If any fix requires changing an acceptance row, stop and hail plan with the row.

Escalation counter reset by this ruling. Back to work.
