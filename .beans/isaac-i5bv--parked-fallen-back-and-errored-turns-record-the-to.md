---
# isaac-i5bv
title: Parked, fallen-back and errored turns record the tokens they spent
status: in-progress
type: bug
priority: high
created_at: 2026-11-17T15:52:03Z
updated_at: 2026-11-17T15:52:36Z
---

Usage accounting, after isaac-21it (a stopped turn keeps its tokens). Repo:
**isaac-agent** (`drive/turn.clj`).

## What is wrong

A turn's cost reaches the session in two places: when a clean reply is
stored, and when a loop result with an error or a cancel is kept. Several
steps that run after the tool loop turn a clean loop into a non-reply, and
then neither happens:

1. **Silence park.** After an empty reply Isaac sends one nudge. If that is
   empty too, the turn is parked with a fresh error that carries no usage.
   Every request of the turn, the nudge included, is lost.
2. **A nudge that errors** drops the loop's usage.
3. **An empty wrap-up** after the cycle limit is parked the same way; a
   wrap-up that succeeds drops the wrap-up request's own usage.
4. **Fallback.** When a walled model hands the turn to the next, only the
   last model's usage is recorded; when no model is left, nothing is.
5. **A mid-turn error in the tool loop** drops the earlier cycles' usage.

A parked turn's `:turn/ended` also has no request or token counts.

Zane Lab counted, on zanebot over 7 days: 153 silence-parked turns, 470
requests, 1.42 million prompt tokens that never reached a session total.
Three sessions reconcile to the token with those left out.

## Acceptance

```gherkin
Scenario: a turn parked for silence keeps every request it made
  Given the provider answers cycle 1 with a tool call costing 1000 prompt and 20 output tokens
  And cycle 2 with an empty reply costing 1200 prompt and 30 output tokens
  And the continuation nudge with an empty reply costing 1300 prompt tokens
  When a turn runs on "s"
  Then the turn is parked with reason :silence
  And session "s" has 3500 input and 50 output tokens
  And :turn/ended has :requests 3 and :prompt-tokens 3500

Scenario: a silence nudge that errors keeps the loop's requests
  Given the same two cycles
  And the nudge answers with an error
  Then session "s" has 2200 input tokens
  And :turn/ended has :requests 3

Scenario: a turn that falls back keeps every model's usage
  Given the head model answers 1 request costing 900 prompt tokens and then walls
  And the fallback model answers costing 1100 prompt tokens
  When a turn runs on "s"
  Then session "s" has 2000 input tokens

Scenario: a tool-loop turn that errors mid-way keeps the earlier cycles
  Given cycle 1 costs 1000 prompt tokens and cycle 2 fails
  When a turn runs on "s"
  Then session "s" has at least 1000 input tokens

Scenario: a parked turn that later resumes is counted once
  Given a turn on "s" was parked for silence after 3500 prompt tokens
  When the turn resumes and answers in 1 request costing 1400 prompt tokens
  Then session "s" has 4900 input tokens
```

- Every turn-end path records usage exactly once. Keep a spec that fails if
  the double-count guard is removed (the lab's mutation: 2000 against 1000).
- A parked turn with no error key (a failed token refresh after a paid
  cycle) and a failed cycle-limit summary are covered too; the reviewer
  found the first patch missed both.
- The lab had two patches for the fallback gap that double-count when
  merged (3700 against 2200 tokens). One rule, one place.
- A clean reply is counted exactly as today.

## Exceptions

Authorized by the planner, 2026-11-17. These feature edits are part of this
bean and are not tampering:

- Existing isaac-agent scenarios whose session token totals or
  `:turn/ended` rows leave out a parked, fallen-back or errored turn's
  requests change to the corrected totals. List each one in the bean with
  the old and new number.
- Any feature file in isaac-agent: adding new scenarios, and new step
  definitions for them, that prove the acceptance above. No existing
  scenario may be removed or weakened beyond what is listed here.

## Working notes

- Approved by Micah 2026-11-17 ("usage accounting improvements are
  welcome"). Ungated: worker, then verifier. The scenarios above are the
  behaviour to prove; write them as feature scenarios where existing steps
  can express them and as specs where they cannot, and say which is which.
- Reference work on zanebot (read-only): `/Users/zane/agents/zane-lab/experiments/silence-usage/`
  (`fix.patch` + `fix.v2.patch`, `REVIEW.md`), `/Users/zane/agents/zane-lab/experiments/tool-loop-usage/`,
  and `/Users/zane/agents/zane-lab/experiments/stop-gaps/` (`agent-on-silence.v2.patch`, `REVIEW.md`; agent
  side only, **do not change Foreman**). The patches were cut against a
  week-old pin stacked on lab patches; take the idea and the specs and write
  the change against today's main, which already has isaac-21it. You own
  the result.
- Do not run the lab's `check.sh` scripts as they are. Never signal a
  process group, and never use `pkill` or `killall`.
- `bb ci` and `bb jvm-spec` pass. No module version bump.
