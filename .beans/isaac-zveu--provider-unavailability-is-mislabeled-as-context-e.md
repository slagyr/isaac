---
# isaac-zveu
title: Provider unavailability is mislabeled as :context-exhausted
status: in-progress
type: task
priority: high
created_at: 2026-09-17T22:53:29Z
updated_at: 2026-09-17T23:21:14Z
---

## Problem

`classify-ended-by` (isaac-agent `src/isaac/drive/turn.clj:447`) collapses provider
unavailability into context exhaustion:

```clojure
(or (:unavailable? result)
    (= :context-exhausted (:reason result))) :context-exhausted
```

Any result carrying `:unavailable?` is reported — and logged on `:turn/ended` — as
though the session ran out of context. A provider outage, an HTTP 429 rate or session
limit, and an auth failure are all unrelated to context size, and they have the
opposite remedy: context exhaustion means compact or start a fresh session, while
unavailability means wait, back off, or fix credentials. Reporting one as the other
sends the operator to the wrong fix.

Ordering note: `(:error result)` is matched first (line 455), so this only bites when
`:unavailable?` is set *without* an `:error` key.

## Field evidence (2026-09-17)

While yopp's `claude-code` provider was returning HTTP 429
(`"You've hit your session limit · resets 6:40pm (UTC)"`), the turn path surfaced
context exhaustion. That label is what led me to misdiagnose the incident as a context
problem before reading the raw provider payload. The wrong label cost real diagnosis
time on a live incident.

## Proposal

Give unavailability its own terminal value — `:provider-unavailable` — add it to
`ended-by-values` (`turn.clj:445`), and reserve `:context-exhausted` for
`(= :context-exhausted (:reason result))` only.

Clean cutover: no alias, no back-compat mapping. Any consumer switching on
`:context-exhausted` to mean "provider down" is already wrong and should be updated.

Ripple to check when implementing: every reader of `:ended-by` / `:turn/ended`
(comm delivery, episode sealing, hail retry/defer logic) — an unavailable turn should
defer and retry, not be treated as a session that needs compacting.

## Acceptance

1. A turn whose result carries `:unavailable?` (no `:error`) reports
   `:ended-by :provider-unavailable`, and `:turn/ended` logs that value.
2. A turn whose result carries `:reason :context-exhausted` still reports
   `:ended-by :context-exhausted`.
3. `:error` results keep reporting `:error` (ordering at line 539 preserved).
4. Green:

       bb spec spec/isaac/drive/turn_spec.clj

## Scenarios (2026-09-17)

Committed `@wip` on isaac-agent `main` @ `7969122`:

- `features/llm/turn_exhaustion.feature:57` — a provider wall ends with
  `:provider-unavailable`, not `:context-exhausted`
- `features/llm/turn_exhaustion.feature:77` — a hard context overflow still ends with
  `:context-exhausted`

The feature narrative's `:ended-by` enumeration was extended to include
`:provider-unavailable` in the same commit. Both scenarios reuse existing steps; no new
steps were invented.

Acceptance commands:

    clojure -M:features features/llm/turn_exhaustion.feature:57
    clojure -M:features features/llm/turn_exhaustion.feature:77
    bb spec spec/isaac/drive/turn_spec.clj

Remove `@wip` when the split lands.

Heads-up, NOT in this bean's scope: `features/llm/provider_walls.feature` lines 97 and
119 still carry `@wip` tags although isaac-bs5b (which implemented them) is completed.
They are stale, not unimplemented behavior — do not treat them as missing work, and do
not "fix" them here.

## Implementation (2026-09-17, scrapper@isaac-work-3)

Root cause confirmed: `classify-ended-by` grouped `:unavailable?` with `:reason :context-exhausted`.

Implemented branch `bean/isaac-zveu` @ `0a8551c` (base `origin/main@7969122`):

- added `:provider-unavailable` to `ended-by-values`;
- genuine `:reason :context-exhausted` remains `:context-exhausted`;
- otherwise `:unavailable?` reports `:provider-unavailable`;
- error ordering remains ahead of unavailability;
- added focused unit examples and removed `@wip` from the two owned scenarios.

Acceptance evidence:

- `bb spec spec/isaac/drive/turn_spec.clj`: 71 examples, 0 failures, 213 assertions.
- provider-wall feature scenario: 1 example, 0 failures, 2 assertions.
- hard-overflow feature scenario currently fails because its single queued HTTP error is consumed by the pre-turn compaction request; the turn then receives Grover's default reply and ends `:reply`. This scenario fixture issue is independent of the classification split; the unit spec proves genuine context exhaustion remains unchanged.

## Conflict (2026-09-17)

The committed hard-overflow scenario cannot exercise the stated behavior as written: its only queued HTTP 400 is consumed by the pre-turn compaction request. The actual turn then receives Grover's default successful reply and ends `:reply`. Removing `@wip` makes acceptance red for a fixture-design reason unrelated to ended-by classification. I restored `@wip` on that scenario only; provider-unavailable scenario and all three unit classifications are green.

Current branch: `bean/isaac-zveu` @ `48dc579` (base `origin/main@7969122`). Planner decision needed: authorize adding a second queued overflow response / otherwise amend the scenario, or move that hard-overflow feature repair to a separate bean while accepting unit coverage here.
