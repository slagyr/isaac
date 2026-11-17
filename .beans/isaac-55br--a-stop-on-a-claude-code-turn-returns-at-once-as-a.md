---
# isaac-55br
title: A Stop on a Claude Code turn returns at once, as a cancelled result that keeps its usage
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-11-17T15:14:41Z
updated_at: 2026-11-17T16:24:36Z
blocked_by:
    - isaac-xhb9
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


## Exceptions

Authorized by the planner, 2026-11-17. These feature edits are part of this bean and are not tampering:

- Existing isaac-claude-code scenarios that expect a cancelled turn to end as `:mcp-unavailable` change to `:cancelled`. List each one in the bean.
- Any feature file in this bean's repo: adding new scenarios, and new step definitions for them, that prove the acceptance above. No existing scenario may be removed or weakened beyond what is listed here.


## Implementation notes (2026-11-17)

- `isaac-claude-code` branch `bean/isaac-55br`, HEAD `9ad1bfc`. Stop returns `:cancelled` with `:message "cancelled"`, `:cancelled? true`, partial content, and per-request usage; cancellation takes precedence over stderr weather. Cancellation captures child handles before SIGTERM and reaps by individual handle after 2.5 s grace, without blocking the turn.
- Acceptance 1 (partial text, prompt return <100 ms, child cleanup within 3 s) is covered by `live_stream_spec.clj` (latency) and `cancel_review_spec.clj` (20 cancels, fast-exit orphans, late-output isolation); acceptance 2, 3 and 4 by `stop_accounting_spec.clj` (real local process, message_start 1200 + cache-read 300 = 1500, one request, weather precedence and provider error contract). Existing feature steps exercise fake CLI cancellation but cannot simulate the slow real child or pre-assistant stream interruption, so these are specs, not new feature scenarios. No existing feature scenario expected a cancelled turn to end as `:mcp-unavailable`; the existing MCP-weather scenarios exercise actual MCP init failures and remain unchanged.
- `bb ci`: 114 specs / 76 features, 0 failures (3 opt-in real-binary specs pending); `bb jvm-spec`: 67 examples, 0 failures (3 opt-in pending). `bb bean-gate verify isaac-55br`: exit 2, no feature-baseline.
