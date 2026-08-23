---
# isaac-mjr4
title: 'Foreman F1: engine, file store, machine config, start/signal/status/list CLI'
status: in-progress
type: task
priority: normal
created_at: 2026-08-23T22:07:07Z
updated_at: 2026-08-23T22:15:17Z
---

First bean of isaac-foreman (repo slagyr/isaac-foreman, scaffold 4cdb1c9, scenarios @wip at 3497ace). Design record: isaac-tdgt (full design + F1 session rulings 2026-08-23), isaac-51xy decisions 30-38.

## Contract (2026-08-23 session — full rulings in isaac-tdgt)

- Machines = config entities: root :machines + config/machines/<name>.edn (:merge-root-entity?). Row keys {:start :event :end :action}; :initial; :* wildcard (EXPLICIT ROW WINS over :* for the same event — spec obligation); optional :states {:s {:entry [...] :exit [...]}}. States are implicitly declared by appearing in rows — only cross-references can dangle (action names, later :parent); referential validation in the standard dialect.
- Actions: machine-local :actions map + shared :foreman {:actions} pool (machine wins). Built-in TYPES: :log (executes in F1, prints :message), :hail / :notify (validate + RECORD as pending; execution = F3). Berth :isaac.foreman/action reserved for new types (not exercised in F1).
- Action ordering on a transition: exit-of-left -> transition :action -> entry-of-entered; recorded in history in execution order.
- Store: default file impl — foreman/<machine>/<instance>.edn {:state :context :pending-actions} + per-instance events.ednl (append-only; history AND persist-before-ack intake are the same file; write history BEFORE acknowledging any signal). Store protocol + :isaac.foreman/store berth (file impl only in F1). Observer SEAM: per-machine :observers, protocol, built-in :log observer (beans-sync observer = F5).
- Unhandled events: warn on stderr with the bounced state, exit 0, record in history as unhandled. Never fatal.
- CLI `isaac foreman start|signal|status|list` via :isaac/cli manifest contribution. Transition echo format `instance: from -> to (event)` shared by signal output and history lines.
- Pure engine isolated in isaac.foreman.machine (parse/validate/step, no I/O) — future standalone extraction candidate.

## Scenarios (2026-08-23, @wip at slagyr/isaac-foreman 3497ace)

features/foreman/machine.feature (3): both-forms validation + dangling action-ref rejection; exit/transition/entry ordering (all-:log observability, both surfaces); :* from two origin states + instance isolation.
features/foreman/cli.feature (4): start/signal/status round-trip across processes (durability implicit) + double-start and unknown-instance errors; record-only actions (pending list = F3 work queue; :log executes and does not pend); unhandled-event robustness + instance unharmed; list with --state filter (current state, not history — beacon-11 round-trips home).

## Step ledger

| step | status |
|---|---|
| Isaac root / config docstring / isaac EDN file table / config validate / fixed clock / CLI run + stdout/stderr/exit | reuse |
| the isaac EDN file ... exists with: | VERIFY keyword values + indexed paths (transitions[0].start); fallback = docstring file step — confirm, don't invent |

No new steps.

## Spec obligations

- machine.clj (pure): parse/validate (dangling refs, duplicate rows), step fn (state x event -> {new-state, actions, ordering}), explicit-beats-wildcard precedence, unhandled result shape.
- file store: instance round-trip vs independently-computed content (decision-8 discipline); events.ednl append-before-ack; concurrent signal safety (file locking or single-writer note).
- observer seam: registered observers see every transition incl. unhandled? (rule: observers see TRANSITIONS only; unhandled events go to history, not observers — F2 revisits for event-door parity).

## Acceptance

In slagyr/isaac-foreman — remove @wip and:
```
bb spec spec
bb features features
```
No dependencies — F1 is self-contained (no isaac-agent changes). NOTE: worker bootstraps CI/deps on first run (skeleton deps pins may need bumping to current agent/foundation SHAs).

## Machine testing story (2026-08-23, Micah's question)

Machines (the config tables) are TDD-able at three layers: (1) **speclj against the pure engine** — isaac.foreman.machine/step is (table, state, event) -> {state, actions, order}, no I/O; authors red-green their EDN entity like normal code (this is a design REQUIREMENT on machine.clj's API, not an accident). (2) **Gherkin** — F1's CLI steps (start/signal/status-matches) ARE the machine-testing DSL; an orchestration ships table + feature file, reviewed like any bean. (3) **Record-only = simulator** — scratch instances against production config are safe in F1; F3 MUST add --dry-run to preserve this once actions execute (recorded as an F3 obligation in isaac-tdgt). Runtime gap detection: unhandled-event history entries show the missing rows.
