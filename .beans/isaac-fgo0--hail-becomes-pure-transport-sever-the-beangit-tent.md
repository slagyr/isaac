---
# isaac-fgo0
title: 'Hail becomes pure transport: sever the bean/git tentacle, remove continuations'
status: draft
type: feature
priority: high
created_at: 2026-07-13T20:44:51Z
updated_at: 2026-07-13T20:44:51Z
---

## Goal (Micah, 2026-07-13)

Make hail a PURE TRANSPORT / agent pub-sub. Sever the bean-orchestration tentacle: remove all bean-awareness, git, and beans-CLI dependencies from the hail delivery worker, and remove the continuation machinery. Orchestration convergence becomes PROMPT-driven (agents drive their own handoffs via hail-send); no code backstop re-queues work.

## The violation being removed

`isaac-hail/src/isaac/hail/delivery_worker.clj` currently runs turns AND branches on bean-workflow state:
- `(:require [isaac.hail.beans-status ...])` — and `beans_status.clj` shells `git -C <beans-dir> ls-tree origin/main -- .beans/`, `git pull`, and `beans show`. A message bus doing version control.
- The turn-outcome `cond` has two continuation branches:
  - `(= :tool-loop-limit (:ended-by result)) -> queue-continuation!` (isaac-5ru9)
  - `(turn-in-limbo? cfg delivery result) -> queue-limbo-continuation!` (isaac-je45) — the bean tentacle.

## Changes

1. **Delete `beans-status` from hail entirely.** No git, no `beans` shell, no `turn-in-limbo?`, no bean-id awareness in the delivery path. Delete `beans_status.clj` from isaac-hail (or move it to wherever the orchestration prompts live if still needed there).
2. **Remove BOTH continuation branches** from the delivery-worker cond. A delivered turn ends in exactly: delivered, error->reschedule (transport retry), unavailable->defer (provider weather), or suspended. NO continuation, NO limbo re-queue.
3. **Hail emits a neutral turn-ended fact** (log event at minimum; a real event hook is the PARKED deterministic-bus bean — do NOT build that here). This just records "turn ended, outcome X, executed-tools Y" without acting on bean state.
4. Orchestration relies on PROMPTS: the "never end a turn in limbo" skill rules stay and are the sole convergence mechanism. A turn that fails to hand off strands the bean VISIBLY (in-progress, no pending hail) — quiet and inspectable, not thrashing.

## Unwinds (state honestly)

This removes the code paths from isaac-je45 (limbo detector), and obsoletes its patches isaac-iv60 and isaac-u91b (they fixed the machinery we are deleting) and isaac-fi41 (escalation-halt — with continuations gone there is nothing to halt; a stuck bean simply stops). Note these as superseded. isaac-5ru9's tool-loop-limit continuation is removed here; what the tool loop does INSTEAD at its cap is the sibling bean (tool-loop rethink #2).

## Scenarios (worker writes; required coverage)

1. A delivered turn that ends WITHOUT a hail-send does NOT re-queue — the delivery concludes (delivered), the bean is left as-is (visible strand). NO continuation, NO beans/git shell in the path.
2. Error still reschedules (transport retry preserved); unavailable still defers (3tvq preserved); suspend still suspends (2xj5 preserved).
3. hail has NO dependency on beans/git — verify the delivery path issues no `beans`/`git` subprocess (spy/assert).
4. A neutral turn-ended fact is recorded with outcome + executed-tools, taking no bean-workflow action.

## Priority

HIGH — this is the architectural cleanup that stops the runaway class (7l5m). Prerequisite for any later orchestration work. Pair with the tool-loop rethink bean.
