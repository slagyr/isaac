---
# isaac-lr8h
title: 'Foreman F3: submit turn actions through Agent'
status: completed
type: feature
priority: normal
created_at: 2026-09-27T22:33:12Z
updated_at: 2026-09-28T05:08:52Z
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


## Planner adjustment (2026-09-28, prowl@isaac-plan) — same tool identity as isaac-tjjm

The allow step writes a keyword. `foreman-signal` is unqualified and Agent rejects it. The registered tool is `:foreman/signal`, wire name `foreman__signal`. Same ruling as isaac-tjjm.

`Then the stdout matches:` is a raw regex on the first cell of each row. A header row is not a column contract. `.` does not cross a newline.

### Contract (isaac-foreman main `c936754`, file still `@wip`)

- Background allow: `foreman/signal`.
- Scenario 1 `tool_call` column: `foreman__signal`.
- Pool wait: three regex rows, `lamp-room`, `dock`, `held`. Not a column table.
- The status regex uses `[\s\S]*` between `via tool` and `unhandled: turn-ended`, so it crosses the newline `history-line` prints.

### Re-baselined

    feature-baseline: isaac-foreman c9367543e694c926cc88f5aa80ba15d8b1c6b41b
    feature-blob: isaac-foreman features/foreman/turn_action.feature 106153df6a0e793ba91ebac1cd90a781c366db63

The file is `@wip`, so the blob names no lines. All four scenarios are this bean's. Drop the file `@wip` only after they pass. Do not land while it is `@wip` on main.

### Worker now

1. Rebase `bean/isaac-lr8h` onto this main. Keep the implementation. Feature diff may only drop the file `@wip`.
2. Do not put the allow token or the tool_call column back to `foreman-signal`.
3. `bb features features/foreman/turn_action.feature` green, then `bb bean-gate verify isaac-lr8h` exit 0, then land.

This note resets the verify-fail counter.

feature-baseline: isaac-foreman c9367543e694c926cc88f5aa80ba15d8b1c6b41b
feature-blob: isaac-foreman features/foreman/turn_action.feature 106153df6a0e793ba91ebac1cd90a781c366db63

## Worker checkpoint (2026-09-28, scrapper@isaac-work-1)

Done: Foreman branch rebased onto c936754; feature diff only removes @wip. CLI --data and retry implemented; focused cli_spec green (6 examples). Submission now wakes after recording request id. Last acceptance run RED (4 failures): status loses submitted id after nested tool signal; Agent worker serializes observer object into held record (EDN read fails); unknown pool error has colon differing from contract; held state not shown. Resume at isaac-agent-lr8h/src/isaac/turn/worker.clj:82 and isaac-foreman/src/isaac/foreman/core.clj:78. Command: clojure -Sdeps '{:aliases {:work-lr8h {:override-deps {io.github.slagyr/isaac-agent {:local/root "../isaac-agent-lr8h"} io.github.slagyr/isaac-agent-spec {:local/root "../isaac-agent-lr8h/spec"}}}}}' -M:work-lr8h:features features/foreman/turn_action.feature. No acceptance green; continue repair before gate/landing.

## Worker checkpoint (2026-09-28, scrapper@isaac-work-1, second pass)

Done: Agent keyed submission, durable observer refs, key propagation through held turn, and CLI --data/retry; agent focused spec green (3 examples), committed/pushed c83d836. Foreman feature RED 2/4 after first key patch (busy pool status + lost-request retry); Foreman changes uncommitted pending acceptance. Next: rerun feature with corrected key propagation and inspect failing step, then bb ci/gate. Resume at isaac-foreman/src/isaac/foreman/core.clj:72 and isaac-agent-lr8h/src/isaac/turn/worker.clj:42. Run `clojure -Sdeps '{:aliases {:work-lr8h {:override-deps {io.github.slagyr/isaac-agent {:local/root "../isaac-agent-lr8h"} io.github.slagyr/isaac-agent-spec {:local/root "../isaac-agent-lr8h/spec"}}}}}' -M:work-lr8h:features features/foreman/turn_action.feature` from isaac-foreman.

## Landed on main (2026-09-28)

main-sha: isaac-agent 2724899d1d7b69f9c4b8239930f4b03dbd631090
main-sha: isaac-foreman 753118c5d9719b4a73f3d3c899fa1f0247fd191f

Agent `bb ci`: 1793 specs / 905 features, 0 failures (one pre-existing pending). Foreman `bb ci`: 42 specs / 16 features, 0 failures. `bb features features/foreman/turn_action.feature`: 4 examples, 0 failures, 21 assertions. `bb bean-gate verify isaac-lr8h`: exit 0 after Foreman squash. Feature diff removes only @wip. `git grep ':hail\|:band'` in Foreman src/resources: no matches.
