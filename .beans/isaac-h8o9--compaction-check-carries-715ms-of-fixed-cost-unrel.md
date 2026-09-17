---
# isaac-h8o9
title: Compaction check carries ~715ms of fixed cost unrelated to transcript size
status: todo
type: task
priority: normal
created_at: 2026-09-17T22:32:49Z
updated_at: 2026-09-17T22:59:03Z
---

## Problem

Every turn pays a compaction check whose cost does not scale with transcript size.
Measured 2026-09-17 on zanebot (agent `bcd6d5e`, i.e. after isaac-3uy9 landed).

Server-side, two sessions of very different size land in the same band:

| session | entries | bytes | elapsed-ms observed |
|---|---|---|---|
| `isaac-verify` | 208 | 480,857 | 711.7 – 722.1 |
| `isaac-work-1` | 537 | 1,305,568 | 717.3 – 723.0 |

2.6x the entries, 2.7x the bytes, same ~715ms.

CLI-side, two matched turns (same crew `marvin`, same model, ollama `llama3.2`,
transcript size the only variable):

| session | entries | compactables | elapsed-ms |
|---|---|---|---|
| tiny scratch | 6 messages | 2 | 2305.2 |
| clone of `isaac-work-2-archive-20260903` | 1005 | 512 | 2331.7 |

167x the entries, 256x the compactables, 1.1% difference.

## What isaac-3uy9 did and did not fix

3uy9 removed the size-dependent term, and that is confirmed working. Pre-deploy CLI
checks ran 4761 / 5456 / 5475 ms at gauges 253K / 134K / 274K, against 2810 ms at
gauge 2 — large sessions cost roughly double small ones. Post-deploy both ends land at
~2.3s. The scaling term is gone. What remains is a constant, and it is what this bean
is about.

## Ruled out by reading the deployed code — do not re-investigate

- `active-tools` / `tool-registry/tool-definitions`: on a warm registry this is an atom
  deref + filter + `dissoc`; the activation loop is guarded by `lookup`, and
  `module/lifecycle activate!` short-circuits on `:already-active`. Tracked separately
  as **isaac-0t2f**, hygiene only.
- `nexus/get-in` -> `necho`: `@root-runtime`, a plain atom deref.
- `compaction/context-gauge`: stamped sums (`last-input` + `last-output` + delta after
  `:tally-after-id`); no walk of the full history.
- `compaction/plan-compaction`: counts and sums stamped tokens without rendering — that
  was 3uy9's change.

## Still unexamined

- `policy/get-session` (session.edn read) and `policy/get-transcript` (current.ednl read).
  Note a transcript read would be *expected* to scale and does not, so either it is
  cached or it is not the cost.
- `compaction/resolve-config`, `api/display-name`.
- Whether the ~3x CLI-vs-server gap (2.3s vs 715ms) is the config-resolution floor
  already tracked as **isaac-v1la**.

## Proposal

Do NOT optimize on a guess. This bean exists because two successive guesses (message
rendering, then tool loading) were both wrong, and one of them shipped.

Instrument first: `run-compaction-check!` already takes `check-ns` at the top
(`turn.clj:1021`). Add per-step deltas to the existing `:session/compaction-check`
debug line, plus `:entry-count` and `:transcript-bytes` so the right independent
variable is recorded rather than `:gauge` (which is a token tally, not a measure of
work, and misled this investigation once already).

One tick of real worker traffic then names the step. Decide the fix after that, with a
number attached to it.

## Acceptance

1. `:session/compaction-check` carries per-step timings and `:entry-count` /
   `:transcript-bytes`.
2. A real worker tick on zanebot attributes the ~715ms to a named step, recorded here.
3. Green:

       bb spec spec/isaac/drive/turn_spec.clj spec/isaac/session/compaction_spec.clj
       clojure -M:features features/session/cycle_timing.feature

## Scenarios (2026-09-17)

Committed `@wip` on isaac-agent `main` @ `7969122`:

- `features/session/cycle_timing.feature:47` — the compaction check reports where its
  own time went

Reuses existing steps; no new steps invented.

Acceptance command:

    clojure -M:features features/session/cycle_timing.feature:47

Remove `@wip` when the instrumentation lands.

## Extra datapoint (2026-09-17)

The same check ran in **241ms** on yopp against **715ms** on zanebot, on identical code
(`bcd6d5e`). The fixed cost is therefore host- or config-dependent, not inherent to the
code path — worth comparing the two hosts' config resolution before assuming the cost
lives in the check itself.
