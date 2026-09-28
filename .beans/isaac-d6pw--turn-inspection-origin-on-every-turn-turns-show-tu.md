---
# isaac-d6pw
title: 'Turn inspection: origin on every turn, turns show, turn_get tool'
status: todo
type: feature
priority: normal
created_at: 2026-09-28T01:52:08Z
updated_at: 2026-09-28T18:24:12Z
parent: isaac-q3u3
blocked_by:
    - isaac-70cr
---

Likely repo: **isaac-agent**. Split out of the Hail handoff design (2026-09-27, Micah + planner): Hail becomes stateless, so the turn is the only thing with an id and a state, and Agent must answer everything `hail_get` used to.

## Contract to plan

- **Origin on every turn request.** The durable request (isaac-70cr TurnStore) carries an opaque `:origin` map supplied by the submitter — for Hail: `:source :hail`, sender (`:from`, `:from-crew`, `:submitter-session`), `:principal`, `:thread-id`, `:reply-to`, `:params`, `:data`. Agent stores and returns it; it never interprets it.
- **`isaac turns show <id>`**: prompt/input, target, origin, state (queued / held / running / terminal), outcome and reason, timestamps.
- **`turn_get` crew tool**: the same record as data. Crews use it where they used `hail_get`.
- Idempotency keys moved to isaac-70cr (2026-09-27). Hail passes a caller-supplied `idempotency-key` (CLI/HTTP/tool) straight through as that submission key.
- **Retention:** finished turn records are kept indefinitely for now (same as Hail records today); pruning is a later decision.

## Scenario plan (to draft)

1. A submitted turn's origin round-trips through `turns show` and `turn_get`.
2. `turns show` reports queued → running → terminal with outcome and reason.
3. An unknown turn id fails clearly.


## Decisions (2026-09-28, Micah + planner) — refine the contract above

Found after isaac-70cr landed: `turns show` exists but prints only id/session/state/outcome (unknown id to stdout); `isaac.turn.submit/submit!` hardcodes `:origin {:kind :foreman}` for every caller; finished records carry no reason or timestamps. Agent tools are `:group/name` (models see `group__name`).

1. **The caller supplies `:origin`** (opaque map). `submit!` stops hardcoding `:foreman`; no origin → `{:kind :submit}`. Foreman passes `{:kind :foreman :machine … :instance …}` (one-line isaac-foreman change in this bean). Plain `prompt` records `{:kind :cli}`. Hail passes its own in isaac-ex4q.
2. **Records gain `:reason`** (error and cancelled outcomes) and `:created-at`, `:started-at`, `:finished-at`.
3. **`turns show <id>` prints the whole record**: id, session/target, input, resource pools, state, outcome, reason, timestamps, merged-into, and each origin field as `origin.<key>: <value>`. Unknown id → stderr `turn not found: <id>`, exit 1.
4. **`turn__get` crew tool** (`:turn/get`), opt-in via `tools.allow`; argument `id`; returns the record as JSON; unknown id → tool error `turn not found: <id>`.
5. **Replaces Hail's inspection surface** (Micah): `hail_get` → `turn__get`; `isaac hail` inspection commands → `turns` commands (enumerated in isaac-ex4q).

## Acceptance

Feature: `isaac-agent/features/turn/turn_inspection.feature` (new, 5 scenarios, `@wip` on main at b22826a). Remove `@wip`; all pass:

- [ ] `bb features features/turn/turn_inspection.feature` — `:18` full show, `:39` error reason, `:53` queued has no start time, `:71` turn__get tool, `:99` unknown id in CLI and tool
- [ ] Unit spec: an arbitrary origin map passed to `submit!` round-trips unchanged through the store and `turns show`.
- [ ] isaac-foreman passes its own origin to `submit!`; its specs/features stay green; repin agent.
- [ ] Existing `turn_store.feature` stays green.
- [ ] `bb verify` green in both repos; version bumps.

feature-baseline: isaac-agent b22826a771b9ac635bdbbad7c4cd44d0049e6047
feature-blob: isaac-agent features/turn/turn_inspection.feature ca26ca32320263b8620edca39566832c39c70bce
