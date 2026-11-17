---
# isaac-fe5e
title: A cancel with no turn running does not cancel the session's next turn
status: todo
type: bug
priority: high
created_at: 2026-11-17T15:14:41Z
updated_at: 2026-11-17T15:14:41Z
---

Part 2a of the chat and Stop work. Repo: **isaac-agent**.

## What is wrong

A cancel that arrives when no turn is running is kept, and the session's
next turn picks it up and ends at once as cancelled. It also leaks when a
cancel lands after a turn ends but before its session is released. Seen
live: a follow-up message came back `cancelled` in 140 ms.

## Acceptance

```gherkin
Scenario: a cancel with no turn running does not cancel the next turn
  Given the following sessions exist:
    | name      |
    | idle-test |
  When the turn is cancelled on session "idle-test"
  Given the following model responses are queued:
    | type | content     | model |
    | text | Still here! | echo  |
  When the user sends "hello" on session "idle-test"
  Then session "idle-test" has transcript matching:
    | type    | message.role | message.content |
    | message | assistant    | Still here!     |

Scenario: a cancel that lands after the turn ended is dropped
  Given a turn on session "idle-test" has ended but its session is not yet released
  When the turn is cancelled on session "idle-test"
  And the user sends "hello" on session "idle-test"
  Then the turn result is not "cancelled"

Scenario: a cancel for a turn that was admitted but has not begun still cancels it
  Given a turn on session "idle-test" is admitted and has not begun
  When the turn is cancelled on session "idle-test"
  Then the turn result is "cancelled"
```

- An idle cancel logs `:bridge/cancel-noop` and keeps nothing.
- A surface that relays a cancel within milliseconds of its request needs a
  way to say "a turn is coming" (the lab's `expect-turn!` / `settle-turn!`);
  isaac-acp will use it in the next bean. Cover the queue-worker release
  path too: the reviewer found the first patch missed it.
- The lab changed five existing tests that had encoded the leak. List each
  one you change and why.

## Working notes

- Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker,
  then verifier. The scenarios above are the behaviour to prove; write them
  as feature scenarios where the repo's existing steps can express them and
  as specs where they cannot, and say in the bean which is which.
- Reference work on zanebot (read-only): `/Users/zane/agents/zane-lab/experiments/stale-cancel/` (`fix.v2.patch`, `REVIEW.md`). Read the experiment's
  `README.md` and `REVIEW.md` first; the reviewer broke the first version
  and says how. The patches were cut against week-old pins and stacked on
  other lab patches, so they will not apply cleanly: take the idea and the
  specs, write the change against today's main. You own the result.
- Do not run the lab's `check.sh` scripts as they are: several kill by
  pattern or by process group. Never signal a process group, and never use
  `pkill` or `killall`.
- `bb ci` and `bb jvm-spec` pass. No module version bump; that happens at
  deploy. A dependency sha pin may move when this bean needs an upstream
  change that has landed.
