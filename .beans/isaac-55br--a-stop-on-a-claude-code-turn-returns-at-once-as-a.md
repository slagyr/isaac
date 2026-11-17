---
# isaac-55br
title: A Stop on a Claude Code turn returns at once, as a cancelled result that keeps its usage
status: todo
type: bug
priority: high
created_at: 2026-11-17T15:14:41Z
updated_at: 2026-11-17T15:14:41Z
---

Part 3a of the chat and Stop work. Repo: **isaac-claude-code**. After the
per-turn cancel bean.

## What is wrong

1. A Stop waits about 2.3 seconds for `claude` to exit before the turn
   returns.
2. A stopped turn is reported as provider trouble
   (`{:error :mcp-unavailable :unavailable? true}`), so every Stop looks
   like an outage in the log, and a Stop with no tools reports no usage.
3. A Stop before Claude's first `assistant` event reports zero tokens,
   although the prompt was billed: `message_start` usage is never read.

## Acceptance

```gherkin
Scenario: a Stop returns at once with the text received so far
  Given a driven claude turn that has streamed "partial"
  When the user cancels the turn
  Then the turn returns within 100 ms with content "partial"
  And the claude process and its children are gone within 3 seconds

Scenario: a Stop is a cancelled result, not provider weather
  Given a driven claude turn that has streamed "partial"
  When the user cancels the turn
  Then the result error is :cancelled with message "cancelled" and :cancelled? true
  And the result has no :unavailable? and no :reason

Scenario: a Stop before the first assistant event keeps the billed prompt
  Given claude has printed message_start with 1200 input and 300 cache-read tokens
  When the user cancels the turn before any assistant event
  Then the result usage has 1500 prompt tokens in 1 request

Scenario: a Stop wins over weather text on stderr
  Given claude's stderr says "usage limit reached"
  When the user cancels the turn
  Then the result error is :cancelled
```

- Capture `claude`'s child processes before sending the kill, not after:
  the reviewer found a fast-exiting `claude` orphaned 8 of 10 children.
  Kill them by pid, one at a time.
- Late output from a stopped `claude` never reaches the next turn.
- The cancelled result always carries `:message`; without it isaac-agent
  turns it into a contract error.

## Working notes

- Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker,
  then verifier. The scenarios above are the behaviour to prove; write them
  as feature scenarios where the repo's existing steps can express them and
  as specs where they cannot, and say in the bean which is which.
- Reference work on zanebot (read-only): `/Users/zane/agents/zane-lab/experiments/zane-chat-v2/` (`stop-fast.v2.patch`, `REVIEW.md`) and `/Users/zane/agents/zane-lab/experiments/stop-accounting/` (`claude-code.patch`, Bean A). Read the experiment's
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
