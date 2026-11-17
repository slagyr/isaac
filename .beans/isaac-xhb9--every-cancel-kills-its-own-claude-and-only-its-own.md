---
# isaac-xhb9
title: Every cancel kills its own claude, and only its own
status: todo
type: bug
priority: high
created_at: 2026-11-17T15:14:56Z
updated_at: 2026-11-17T15:14:56Z
blocked_by:
    - isaac-ftrd
---

Part 2b of the chat and Stop work. Repo: **isaac-claude-code**. After the
streaming bean.

## What is wrong

Only the first cancel in a server's life kills its `claude` process. A
process-wide flag that only test helpers reset blocks every later one: the
second and third Stops in one sandbox ran 44 and 35 seconds. A second
process-wide value lets one session's cancel kill another session's
`claude`.

## Acceptance

```gherkin
Scenario: every cancel in one server process kills its turn's claude
  Given a Claude Code command that runs for 8 seconds unless killed
  When a turn on session "s1" starts and is cancelled after 0.4 seconds
  And a turn on session "s2" starts and is cancelled after 0.4 seconds
  And a turn on session "s3" starts and is cancelled after 0.4 seconds
  Then each of the three turns ends within 3 seconds of its cancel
  And the log has 3 entries with event ":claude/driver-terminated"

Scenario: a cancel reaches only its own session's claude
  Given a Claude Code command that runs for 8 seconds unless killed
  When turns on sessions "a" and "b" are running
  And the turn on session "a" is cancelled
  Then the turn on session "a" ends within 3 seconds
  And the turn on session "b" is still running 0.5 seconds later
```

- These need a real subprocess: the fake CLI is reset by every
  `Given a fake Claude Code` step and cannot show a process-lifetime bug.
  Specs are fine.
- Cancel state is per turn. No process-wide cancel state remains.

## Working notes

- Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker,
  then verifier. The scenarios above are the behaviour to prove; write them
  as feature scenarios where the repo's existing steps can express them and
  as specs where they cannot, and say in the bean which is which.
- Reference work on zanebot (read-only): `/Users/zane/agents/zane-lab/experiments/claude-cancel-global/` (`fix-on-v2-stream.patch` is the form that sits on the streaming change). Read the experiment's
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
