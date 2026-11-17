---
# isaac-21it
title: A stopped turn keeps its tokens, never falls back, and logs at info
status: todo
type: bug
priority: high
created_at: 2026-11-17T15:14:56Z
updated_at: 2026-11-17T15:14:56Z
blocked_by:
    - isaac-fe5e
---

Part 3b of the chat and Stop work. Repo: **isaac-agent**.

## What is wrong

1. Usage is kept only for results with `:error`. A cancelled result without
   one loses its tokens: they never reach the session.
2. `:turn/ended` for a stopped turn carries no requests or token counts.
3. `drive!` asks about fallback before it checks for a cancel, so a Stop
   that races a connection error records a provider wall (30 minutes by
   default) and runs the turn again on the next model.
4. Every Stop logs a level-error `:chat/stream-error`.

## Acceptance

```gherkin
Scenario: a stopped turn's tokens are kept in the session
  Given session "s" has 1000 input and 10 output tokens
  When a turn on "s" is cancelled after a request of 1500 prompt and 40 output tokens
  Then session "s" has 2500 input and 50 output tokens

Scenario: :turn/ended reports what a stopped turn cost
  When a turn is cancelled after 1 request of 1500 prompt tokens
  Then the log has :turn/ended with :ended-by :cancelled, :requests 1, :prompt-tokens 1500

Scenario: a Stop never walks the model chain
  Given the crew has a fallback model
  And the head model's result is :connection-lost
  When the user cancelled the turn before the result came back
  Then the turn result is "cancelled"
  And the fallback model is never called
  And no provider wall is recorded

Scenario: a Stop logs at info
  When a turn is cancelled
  Then the log has :chat/cancelled at info
  And the log has no :chat/stream-error
```

- No double count: a stopped turn's usage is added exactly once.
- A bare `{:error :cancelled}` with no `:message` must not become a
  contract error (the reviewer's finding against the first patch).

## Working notes

- Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker,
  then verifier. The scenarios above are the behaviour to prove; write them
  as feature scenarios where the repo's existing steps can express them and
  as specs where they cannot, and say in the bean which is which.
- Reference work on zanebot (read-only): `/Users/zane/agents/zane-lab/experiments/stop-accounting/` (`agent.v2.patch`, `REVIEW.md`, Bean B). Read the experiment's
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
