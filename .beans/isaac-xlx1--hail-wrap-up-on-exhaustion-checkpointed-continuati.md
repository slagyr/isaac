---
# isaac-xlx1
title: 'Hail: wrap-up on exhaustion, checkpointed continuations per band, continuation budget → attention, band :cycle-limit override'
status: in-progress
type: feature
priority: high
created_at: 2026-09-08T15:28:25Z
updated_at: 2026-09-08T20:05:23Z
parent: isaac-ntt6
blocked_by:
    - isaac-y802
---

Repo: isaac-hail. Child of isaac-ntt6 (decisions 4, 5, 6, 8). Blocked by the agent child (Comm on-exhausted + :ended-by). Feature: `features/delivery.feature` (3 @wip scenarios planted on main e497905; the isaac-fgo0 scenario 'tool-loop-limit ends as delivered without re-queue' is REPLACED — this reverses fgo0's removal of continuations, now that continuations are checkpointed).

## Contract
- The delivery worker stops using `null-comm` for the turn's comm: it supplies a hail comm (extend Comm with `comm/defaults`) whose `on-exhausted` answers :wrap-up. Everything else stays noop/send.
- When the turn result has `:ended-by :cycle-limit` (and no :error), the delivery is NOT delivered: the worker re-queues the SAME delivery to deliveries/ with `:continuation` incremented (attempts untouched, bound-session kept) and logs `:hail/turn-continued :session :continuation N`. The next tick runs a fresh turn on the same session (prompt rebuilt from the transcript + the wrap-up note).
- Band config gains `:continuations` (default 3, via bands/with-band-defaults) and `:cycle-limit` (optional; when present the worker puts it on the charge as the cycle-limit override). When a wrapped-up turn arrives with `:continuation` ≥ the band's budget: dead-letter to failed/ with `:reason :continuations-exhausted`, log `:hail/continuations-exhausted :session :continuation :budget` at :error, and post attention (bulletin to the comm outbox, same channel as dead-letter attention).
- An exhausted turn that ended `:error :empty-terminal-response` is a failed attempt as today (k4mf scenario unchanged).

## Step ledger
All existing (agent library + hail's tick step): isaac EDN file exists with (crew, hail band, delivery record), sessions exist, built-in tools registered, model responses queued, the hail delivery worker ticks at, the turn ends on session, the isaac file exists / does not exist / EDN contains, the log has entries matching, the memory comm has events matching. New steps: none.

## Acceptance
- `bb features features/delivery.feature` → the 3 ntt6 scenarios green with @wip removed; the k4mf scenario and all others unchanged and green
- `bb features && bb spec` green in isaac-hail
- Deploy note: zanebot bands isaac-work/isaac-verify/tono-work/tono-verify inherit `:continuations 3`; no config change required
