---
# isaac-1d7x
title: 'Suite health (isaac-agent): CI flakes parallel_tool_batches:124 + compaction_logging:140'
status: scrapped
type: bug
priority: high
tags:
    - suite-health
created_at: 2026-09-08T20:05:06Z
updated_at: 2026-09-10T23:54:39Z
---

Ambient full-suite flakes on `isaac-agent` that failed GitHub Actions CI Tests on land SHA `64f4ca7ea7d78fb6f8e6e0e814cd804127a8a317` (run 34271321013, `bb ci` / `bb features`) while **isaac-kbu0** was landing. They are **not** the MCP registry change. **isaac-kbu0 remains completed.** Do not reopen it.

Current `origin/main` `8d9dd2602d610db45bbd41f142b81152fa85c95c` (**isaac-y802**) CI Tests run 34271982954 is success. Correlation rule: do not commission an independent repair against the kbu0 land SHA.

## Observed (2026-09-08, CI run 34271321013, land SHA 64f4ca7)

1. `features/session/parallel_tool_batches.feature:124` — mixed concurrent batch events
2. `features/session/compaction_logging.feature:140` — partial-compact transcript mismatch

Related, already-documented ambient flakes (do not duplicate the product):
- **isaac-y802** handoff: full-suite flakes already on main (`compaction_memory_flush` memory_write persist; `parallel_tool_batches` cancel mid-batch events). Isolated re-runs of those files go green.
- **isaac-j2v0** (completed): parallel tool-batch product; prior CI repairs for scheduler-order assertions (`8f6038c`, `668f157`).
- **isaac-qkqm** (draft): compaction logging suite health — max-attempt stop + toolCall/toolResult pairing. Different named rows than `:140`.
- **isaac-tx3j** (draft): `episodes/live.feature:604` full-suite flake.

## This bean owns

Harden the two named scenarios so they are suite-stable without weakening intent.

1. Reproduce each failure in isolation (`bb features <file>:<line>`) and in the full suite on current `isaac-agent` main. Record whether it is isolated, full-suite-only, or CI-only.
2. Name the cause: fixture race, event-order assertion vs completion-order contract, transcript matcher drift, or cross-feature state leak.
3. Fix the fixture/assertion/leak. **Do not** weaken scenario intent. **Do not** `@wip` a scenario without a dedicated bean owning its return.
4. If a row belongs to an existing owner (j2v0 product, qkqm compaction-logging health), hand it there and record the handoff instead of duplicating the work.

## Acceptance

    cd isaac-agent
    bb features features/session/parallel_tool_batches.feature
    bb features features/session/compaction_logging.feature

0 failures on each, repeated enough to show `:124` and `:140` no longer flake. Then record an unwrapped `clojure -M:features` run (or CI Tests success on a SHA that only changes these two files). The 180s `bb features` wrapper timeout under load is not by itself a red.

Do **not** reopen isaac-kbu0. Do **not** require MCP registry or Claude Code field checks here.


## Extended (2026-09-10, prowl@isaac-plan) — also owns `:101` cancel mid-batch

CI FAIL hail `df8c1e40` on isaac-qpdb land SHA `58982c6` (run 34488061619) and release 0.1.59 `74b9acd` (run 34488386985) both red at:

- `features/session/parallel_tool_batches.feature:101` — cancel mid-batch memory-comm events (`Expected truthy false`)

Isolated JVM `clojure -M:features features/session/parallel_tool_batches.feature:85` is **1/1 on both 0.1.58 (full CI SUCCESS) and 58982c6**. Diff `837b6d4..58982c6` is `registry.clj` + `registry_spec` only; `turn.clj` unchanged. Blocking mock returns `{:error :cancelled}`; after qpdb that map is preserved so `on-tool-cancel` *should* fire for the in-flight tool. The failing Then is still the **first events table** (anchor tool-call/tool-result) — timing/event-shape, not the preserve branch.

**isaac-qpdb remains completed.** Do not recut `run-handler`. Do not reopen qpdb.

This bean now owns both named parallel_tool_batches rows:
- `:124` mixed concurrent batch events (original)
- `:101` cancel mid-batch memory-comm events (this extension)

`compaction_logging.feature:140` still in scope as originally filed.

## Acceptance (supersedes the file list above)

    cd isaac-agent
    bb features features/session/parallel_tool_batches.feature
    bb features features/session/compaction_logging.feature

0 failures on each, repeated enough to show **`:101`, `:124`, and `:140`** no longer flake. Isolated `clojure -M:features features/session/parallel_tool_batches.feature:85` must also be 0. Record run counts. Do not weaken scenario intent.

## Reasons for Scrapping

Folded into isaac-x4mr (2026-09-11), which owns the whole full-suite flake family; the parallel_tool_batches:101 row was a real qpdb regression fixed on main (f4813d9).
