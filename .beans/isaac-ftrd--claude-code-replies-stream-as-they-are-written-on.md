---
# isaac-ftrd
title: Claude Code replies stream as they are written, on turns that offer tools
status: in-progress
type: feature
priority: high
created_at: 2026-11-17T15:14:41Z
updated_at: 2026-11-17T15:25:18Z
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


## Exceptions

Authorized by the planner, 2026-11-17. These feature edits are part of this bean and are not tampering:

- Existing isaac-claude-code scenarios that assert a reply arrives as one chunk on a turn with tools may change to expect streamed chunks whose concatenation is the same text. List each one in the bean.
- Any feature file in this bean's repo: adding new scenarios, and new step definitions for them, that prove the acceptance above. No existing scenario may be removed or weakened beyond what is listed here.

## Implementation and evidence (2026-11-17)

- `isaac-claude-code` branch `bean/isaac-ftrd`, commit `306480a`. CLI stdout is consumed line-by-line with callback before process exit, retaining output for final parsing, usage and quota. The manifest and feature fixture now permit streaming when tools are offered; the provider restores the final-cycle reply after Isaac joins streamed asides into `:content`. Non-delta older assistant streams still send one final chunk. No version change.
- Acceptance feature scenarios: `features/llm/api/claude_driver.feature` (four chunks with tools, aside + final reply on memory comm, one stdin line/invocation + usage). The feature fixture registers the manifest-equivalent flag. Specs: `spec/isaac/provider/claude_code/live_stream_spec.clj` uses a child waiting for the callback marker to prove pre-exit delivery, four literal chunks, thinking order, cancel, usage, one request and equal stdin/usage with `api/chat`, and provider-driver aside dedup. Exact wall-time behavior cannot be tested by a fake synchronous result alone; marker handshake proves no wait-to-exit before the first chunk.
- Latency finding: no extra request (one CLI invocation and identical stdin on streaming and nonstreaming tool turns), no added exit wait (callback fires before the child releases its marker; normal code waits for EOF/exit on *both* paths). Lab's first three ~200-word after turns took 8.4–9.4s vs 4.5–6.5s before, but the interleaved 22-case replay gate found **median total 6.0s with streaming vs 6.1s without**, p90 10.6s vs 11.3s, median first chunk 4.5s vs 6.0s; c02 repeated four times with both arms passing. The 2–3s lab gap was model/run variance (different output tokens and pre-text thinking time), not a measured fixed streaming overhead. The ~0.4–0.6s from last delta to turn end is the normal remaining Claude `message_stop`/`result` and process exit; it was previously hidden inside the single final chunk. Replay has one sample for most cases, so this is evidence of noise rather than a claim of zero overhead in every workload.
- `bb ci`: 96 specs/0 failures/3 opt-in real pending, 73 features/0 failures; `bb jvm-spec`: 67 examples/0 failures/3 opt-in pending. No lab check.sh invoked (unsafe process-killing scripts). Lab streaming README exists, but no REVIEW.md in that experiment; read `claude-provider-fix/REVIEW.md` and `zane-chat-v2/REVIEW.md` for review caveats.
