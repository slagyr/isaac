---
# isaac-oas8
title: Queue worker creates a missing named session with the crew its frequencies name
status: completed
type: bug
priority: high
created_at: 2026-10-06T20:23:23Z
updated_at: 2026-10-06T20:45:15Z
---

Likely repo: **isaac-agent**. Found by Foreman pilot 1 (isaac-8uno on zanebot), 2026-10-06.

## Why

The bean-work machine addresses its worker turn as
`{:crew "scrapper" :session "bean-<id>" :create :if-missing}`. The resolver is right: for a
missing named session it returns the key plus `:create-identity {:crew "scrapper"}`
(`frequencies.clj` explicit-session branch). The queue worker drops it:
`worker.clj` `wake-charge` only creates a session when the target has NO `:session-key`
(`(or (:session-key target) (when (:create? target) …create…))`). A named session always has a
key, so nothing is created, and the turn is built for a session that does not exist with the
default crew (zane). On zanebot it logged `:session/behavior-resolved :crew "zane"` every tick and
never ran.

`:crew` picks the session; when `:create` makes one, `:crew` is the new session's crew.
`:with-crew` stays a per-turn override and is not the fix.

## Design

- In `wake-charge`, a target with `:create? true` is created whether or not it has a key — a
  named key creates that session, with `:create-identity` (crew, tags), then the turn runs there.
- New step for the scenario: `a turn with input "<text>" is submitted with frequencies:` with a
  `key | value` table (`session`, `crew`, `create`, …), submitted the way the existing
  `is submitted to crew` steps do, capturing `#turn-id`.

## Acceptance

- isaac-agent `features/turn/session_selection.feature:193`.
- The rest of the isaac-agent features stay green.
- Exception: native `bb ci` / `bb spec` may fail only `spec/isaac/agent/session/session_steps_spec.clj:71` (`isaac.foundation.fs/instance: no filesystem available`). Reproduced on clean origin/main (1887 examples, 1 failure); the focused spec passes. That failure is not this bean. Do not edit that spec to make this bean land. `bb features` and `bb bean-gate verify` must be green.

feature-baseline: isaac-agent 45b19b48ccb55fd91fe609654907c8f818555b65
feature-blob: isaac-agent features/turn/session_selection.feature 4e9f86bb5467d3695e021cee2cf65a9ca5154e5d 193

## Checkpoint (2026-10-06)

Done: `bean/isaac-oas8` at `0c02abb` is committed and pushed; `src/isaac/agent/turn/worker.clj:48` creates missing named sessions with the resolved crew, `spec/isaac/agent/turn/queue_steps.clj:230` submits table frequencies, the acceptance scenario and focused worker specs pass, full `bb features` passes (876 examples, 0 failures, 1 existing pending), and `bb bean-gate verify isaac-oas8 --dir isaac-agent=../isaac-agent-oas8` passes. Last test was RED: rebase `bb ci` fails in unrelated `spec/isaac/agent/session/session_steps_spec.clj:71` (`isaac.foundation.fs/instance: no filesystem available`); the same full `bb spec` fails on clean `origin/main` (1887 examples, 1 failure) while that test passes focused. Planner was hailed with this blocker (hail `5ebaa33b`). Next: resume at `spec/isaac/agent/session/session_steps_spec.clj:71`; check whether a mainline fix lands, rerun `bb ci`, then gate and land only after full suite green. No unrelated test edits made.

## Planner adjustment (2026-10-06, prowl@isaac-plan)

The pre-existing suite failure is excluded. `session_steps_spec.clj:71` fails on clean origin/main the same way it fails on `bean/isaac-oas8`: `isaac.foundation.fs/instance` has no filesystem. Focused, it passes. It is not this bean.

`bb features` and the gate must be green. Native `bb ci` may fail only that one example. Do not edit `session_steps_spec.clj` here. Land when the gate is green and that is the only suite failure.

Filed as the same class as draft isaac-7ev2. A suite repair belongs on its own bean, not this one.

## Landed on main (2026-10-06)

main-sha: isaac-agent e05614ac84273ac3f3865643ab883153c182dd62

Re-ran `bb ci` on the rebased bean branch: 1888 specs and 876 features passed (one pre-existing pending); squash gate passed on `main` prior to push. The order-dependent full-suite failure noted above did not reproduce on the final run.
