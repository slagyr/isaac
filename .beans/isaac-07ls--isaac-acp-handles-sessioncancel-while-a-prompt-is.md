---
# isaac-07ls
title: isaac acp handles session/cancel while a prompt is running
status: todo
type: bug
priority: high
created_at: 2026-11-17T15:14:56Z
updated_at: 2026-11-17T15:14:56Z
blocked_by:
    - isaac-fe5e
---

Part 2c of the chat and Stop work. Repo: **isaac-acp**. After the
isaac-agent stale-cancel bean, whose API it uses.

## What is wrong

`isaac acp` reads one line from stdin and runs it to completion before
reading the next. A `session/cancel` sent during a prompt is not read until
the prompt has finished, so Stop does nothing for the whole turn (23 seconds
in the lab), and then cancels the *next* prompt. It affects local
`isaac acp` and `isaac remote … acp`.

## Acceptance

```gherkin
Scenario: session/cancel on stdin interrupts a running session/prompt
  Given the following sessions exist:
    | name        |
    | cancel-test |
  And the LLM response is delayed by 30 seconds
  And the delayed LLM response is released after 5 seconds
  And stdin is:
    """
    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
    {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"cancel-test","prompt":[{"type":"text","text":"Long task"}]}}
    {"jsonrpc":"2.0","method":"session/cancel","params":{"sessionId":"cancel-test"}}
    """
  When isaac is run with "acp --session cancel-test"
  Then the stdout has a JSON-RPC response for id 2:
    | key               | value     |
    | result.stopReason | cancelled |
  And the exit code is 0

Scenario: a session/cancel with no prompt running does not cancel the next prompt
  Given the following sessions exist:
    | name      |
    | idle-test |
  And stdin is:
    """
    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}
    {"jsonrpc":"2.0","method":"session/cancel","params":{"sessionId":"idle-test"}}
    {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"idle-test","prompt":[{"type":"text","text":"hello"}]}}
    """
  When isaac is run with "acp --session idle-test"
  Then the stdout has a JSON-RPC response for id 2:
    | key               | value    |
    | result.stopReason | end_turn |
  And the exit code is 0
```

- New step: `the delayed LLM response is released after {n} seconds`. It
  bounds the fake model's open-ended delay so a failing run ends in seconds.
- Prompts run off the reader thread; requests for one session stay in order;
  two responses never interleave inside one stdout line; the process waits
  for running prompts at end of input.
- No idle-cancel guard of its own: use the agent's `expect-turn!` /
  `settle-turn!` from the stale-cancel bean, and move this repo's
  isaac-agent pin to a sha that has it.
- Five cancel test fixtures run `sleep 30`, which isaac-agent's exec now
  refuses; switch them to a loop (the lab's `acp-pin-drift/fix.patch`).
- The lab found a stray paren that silenced 12 of 29 cli specs. Confirm
  every spec in the file runs.

## Working notes

- Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker,
  then verifier. The scenarios above are the behaviour to prove; write them
  as feature scenarios where the repo's existing steps can express them and
  as specs where they cannot, and say in the bean which is which.
- Reference work on zanebot (read-only): `/Users/zane/agents/zane-lab/experiments/stale-cancel/acp-cancel-noguard.patch`, `/Users/zane/agents/zane-lab/experiments/acp-cancel/` (`BEAN.md`; do not use its `fix.patch`, the reviewer says its guard leaks), `/Users/zane/agents/zane-lab/experiments/acp-pin-drift/`. Read the experiment's
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
