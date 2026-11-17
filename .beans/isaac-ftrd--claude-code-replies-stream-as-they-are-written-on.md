---
# isaac-ftrd
title: Claude Code replies stream as they are written, on turns that offer tools
status: in-progress
type: feature
priority: high
created_at: 2026-11-17T15:14:41Z
updated_at: 2026-11-17T15:15:40Z
---

Part 1 of the chat and Stop work. Repo: **isaac-claude-code**.

## What is wrong

A reply from a Claude Code model arrives all at once. The provider can
stream, but its manifest says `:stream-supports-tool-calls false`, and every
real turn carries tools, so Isaac never takes the streaming path. Turning it
on exposes a second bug: text the model writes before a tool call is stored
twice.

## Acceptance

```gherkin
Scenario: a reply streams as it is written, on a turn that offers tools
  Given a fake Claude Code that writes "Three moorings, all sound." in 4 text deltas
  And the turn offers tools
  When the user sends "count the moorings"
  Then the comm receives 4 text chunks before the turn ends
  And the stored reply is "Three moorings, all sound."

Scenario: text written before a tool call is stored once
  Given a fake Claude Code that writes "Checking." then calls a tool then writes "Done."
  When the user sends "check the gauge"
  Then the transcript holds "Checking." exactly once

Scenario: streaming costs no extra request
  Given a fake Claude Code that streams a reply
  When the user sends "hello"
  Then the fake Claude Code received the same number of stdin lines as without streaming
  And the turn's usage equals the non-streaming turn's usage
```

- The lab saw streamed turns run 2 to 3 seconds longer in total and could
  not explain it. Find out why before landing (an extra request, a wait on
  exit, or noise) and write the answer in the bean.
- Comms that do not stream (iMessage, Discord) get the same final text as
  today, once.

## Working notes

- Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker,
  then verifier. The scenarios above are the behaviour to prove; write them
  as feature scenarios where the repo's existing steps can express them and
  as specs where they cannot, and say in the bean which is which.
- Reference work on zanebot (read-only): `/Users/zane/agents/zane-lab/experiments/zane-chat-streaming/` (`stream.patch`), and `/Users/zane/agents/zane-lab/experiments/replay-gate/` for the 22-case quality check. Read the experiment's
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
