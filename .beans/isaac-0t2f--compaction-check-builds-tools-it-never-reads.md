---
# isaac-0t2f
title: Compaction check builds :tools it never reads
status: todo
type: task
priority: low
created_at: 2026-09-17T22:32:02Z
updated_at: 2026-09-17T22:56:28Z
---

## Problem

`run-compaction-check!` (isaac-agent `src/isaac/drive/turn.clj:1020`) opens by calling
`compaction-estimate-opts` (`turn.clj:1008`), which calls `active-tools` ->
`tool-registry/tool-definitions` to build the full tool-definition vector.

None of the three things the check actually computes reads it:

| call | signature | reads `:tools`? |
|---|---|---|
| `compaction/context-gauge` | `(entry tx pending-input)` | no |
| `compaction/plan-compaction` | `(tx entry context-window)` | no |
| `compaction/resolve-config` | `(entry context-window)` | no |

`:tools` is only needed downstream by `perform-compaction!` / `start-async-compaction!`,
which fire rarely. Every other check computes it and throws it away.

## Measured cost: negligible — this is hygiene, NOT a performance fix

Measured 2026-09-17 on zanebot (agent `bcd6d5e`). Do not let this bean be sold as a
speedup; the numbers below are the reason.

`tool-definitions` on a warm registry is an atom deref + filter + `dissoc`. The
activation loop is guarded by `(when-not (or (glob-token? token) (lookup wire)) ...)`,
so registered tools never re-activate, and `module/lifecycle activate!` short-circuits
on `(contains? @activated-modules* id)` -> `:already-active`.

Two matched CLI turns (same crew `marvin`, same model, ollama `llama3.2`, transcript
size the only variable):

| session | transcript entries | compactables | elapsed-ms |
|---|---|---|---|
| tiny scratch | 6 messages | 2 | 2305.2 |
| clone of `isaac-work-2-archive-20260903` | 1005 entries | 512 | 2331.7 |

167x the entries and 256x the compactables for a 1.1% difference. The fixed cost in
the check is real but it is NOT this call. Tracked separately.

## Proposal

Defer `:tools` so it is computed only on the compaction path — a `delay`, or compute it
inside `perform-compaction!` / `start-async-compaction!` from the opts they already receive.

## Acceptance

Per the project's standing rule, a removal check is a ONE-TIME acceptance criterion,
never a permanent scenario — do not add an absence scenario asserting `active-tools`
goes uncalled.

1. One-time check (not committed as a scenario): with no compaction firing, the check
   path does not call `active-tools` / `tool-definitions`.
2. Compaction still works when it does fire.
3. Green:

       bb spec spec/isaac/drive/turn_spec.clj spec/isaac/session/compaction_spec.clj
       clojure -M:features features/session/cycle_timing.feature
