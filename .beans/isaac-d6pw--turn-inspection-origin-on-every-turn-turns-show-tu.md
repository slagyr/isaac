---
# isaac-d6pw
title: 'Turn inspection: origin on every turn, turns show, turn_get tool, idempotency key'
status: draft
type: feature
created_at: 2026-09-28T01:52:08Z
updated_at: 2026-09-28T01:52:08Z
parent: isaac-q3u3
blocked_by:
    - isaac-70cr
---

Likely repo: **isaac-agent**. Split out of the Hail handoff design (2026-09-27, Micah + planner): Hail becomes stateless, so the turn is the only thing with an id and a state, and Agent must answer everything `hail_get` used to.

## Contract to plan

- **Origin on every turn request.** The durable request (isaac-70cr TurnStore) carries an opaque `:origin` map supplied by the submitter — for Hail: `:source :hail`, sender (`:from`, `:from-crew`, `:submitter-session`), `:principal`, `:thread-id`, `:reply-to`, `:params`, `:data`. Agent stores and returns it; it never interprets it.
- **`isaac turns show <id>`**: prompt/input, target, origin, state (queued / held / running / terminal), outcome and reason, timestamps.
- **`turn_get` crew tool**: the same record as data. Crews use it where they used `hail_get`.
- **Optional idempotency key on submission.** A submitter may pass a key; a second submission with the same key returns the existing turn id and creates nothing. Hail passes a caller-supplied `idempotency-key` (CLI/HTTP/tool) straight through. Without a key every submission is a new turn.
- **Retention:** finished turn records are kept indefinitely for now (same as Hail records today); pruning is a later decision.

## Scenario plan (to draft)

1. A submitted turn's origin round-trips through `turns show` and `turn_get`.
2. `turns show` reports queued → running → terminal with outcome and reason.
3. A repeated idempotency key returns the same turn id and runs one turn.
4. An unknown turn id fails clearly.
