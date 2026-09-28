---
# isaac-lr8h
title: 'Foreman F3: submit turn actions through Agent'
status: todo
type: feature
priority: normal
created_at: 2026-09-27T22:33:12Z
updated_at: 2026-09-28T00:54:01Z
parent: isaac-q3u3
blocked_by:
    - isaac-tjjm
    - isaac-70cr
    - isaac-ey7a
---

Likely repo: **isaac-foreman**, consuming the Agent turn-submission contract. Design: Micah + planner, 2026-09-27. Depends on durable event intake and candidate/resource admission.

## Contract to plan

- Add native `:turn` action execution. A handled transition records the pending action, submits an idempotent Agent turn request with session frequencies and `:resource-pools`, then records the returned request ID.
- Submission failure leaves an inspectable pending action for retry. A retry cannot create a second turn for the same transition/action identity.
- Turn outcome observations return through Foreman's event intake and drive table transitions, including success, error, death, and completion without the expected crew signal.
- Foreman decides workflow state and which logical pool is requested; it never selects a session or directory and does not depend on Hail. Optional `:hail` action remains a separate messaging extension.

## Scenario plan to review

1. One transition submits one durable turn request and records its ID.
2. A repeated action submission creates no duplicate turn.
3. No eligible resource holds the request; resource release starts it and the outcome advances the machine.
4. A turn that ends without the expected signal follows the machine's backstop row.

Draft until scenarios are committed and baselined. Cleanly supersede F1's recorded-but-unexecuted `:hail`-as-orchestration-action assumption where the table uses `:turn`.


## Decisions (2026-09-27, Micah + planner)

1. **Foreman passes `:frequencies` and `:resource-pools` through to Agent untouched**; it never selects a session or a directory. So this bean does not wait on isaac-l3vb — scenarios address a session directly; tag/crew targeting arrives with l3vb and needs no Foreman change. It does wait on isaac-70cr (only Agent can refuse a duplicate) and isaac-ey7a (scripted-pool steps).
2. **Action shape:** `{:type :turn :frequencies {…} :resource-pools [...] :prompt "…"}`. The prompt fills `{{machine}}`, `{{instance}}`, `{{data.<key>}}` from the triggering event — a small substitution in Foreman.
3. **Foreman's turn observer rides every submitted turn** (ref `foreman:<machine>/<instance>`), so outcomes return through isaac-tjjm's events.
4. **Timing:** submit only after the transition is fully persisted (the turn may run in-process and signal the same instance). Success records the Agent request id on the action (`<action> (turn) submitted <id>` in status). Failure keeps it pending with the error (`pending: <action> (turn) failed: <reason>`). `isaac foreman retry <machine> <instance>` and server start resubmit pending actions.
5. **Idempotency key:** `<machine>/<instance>/<event-id>/<action>`; Agent refuses a second request with the same key and answers with the existing request id.
6. **Foreman and Hail are unrelated (Micah).** Remove F1's `:hail` action type and `:band` field from Foreman's schema — clean cutover. The cli.feature scenario "actions are recorded as pending; only log actions execute" (which used `:hail`) was deleted from main in 35b5723. `:notify` is untouched.
7. `foreman signal` gains `--data <edn>` (the human door can carry event data like the tool and HTTP doors).

## Acceptance

Feature: `isaac-foreman/features/foreman/turn_action.feature` (new, 4 scenarios, `@wip` on main at 35b5723). Remove `@wip`; all pass:

- [ ] `bb features features/foreman/turn_action.feature` — `:35` submit + signal, `:54` pool wait + backstop, `:88` refused → retry, `:120` retry after lost request id makes no second turn
- [ ] One-time check: `git grep -n ':hail\|:band'` in isaac-foreman src/resources finds nothing.
- [ ] Existing `cli.feature`, `machine.feature`, `events.feature` stay green.
- [ ] Repin isaac-agent to the main sha that lands isaac-ey7a and isaac-70cr; `bb verify` green; version bump.

feature-baseline: isaac-foreman 35b572383b33254bbbb239a5fb22d26914232281
feature-blob: isaac-foreman features/foreman/turn_action.feature 4ce6dbf2c068bf18c0ae7f1aa9f64c412bf3518d
