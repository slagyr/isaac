---
# isaac-02s2
title: isaac-acp reports tool calls with the right kind and status
status: todo
type: bug
priority: normal
created_at: 2026-11-17T15:14:41Z
updated_at: 2026-11-17T15:14:41Z
---

Part 4 of the chat and Stop work. Repo: **isaac-acp**. After the ACP cancel
bean.

## What is wrong

ACP clients (Toad, the chat page) draw a tool call from its `kind` and
`status`. isaac-acp reports most tools with the wrong kind, reports a failed
tool as `completed`, and one feature never allowed exec at all, so it only
ever tested "unknown tool".

## Acceptance

```gherkin
Scenario: exec tool calls report kind execute
  Given the crew "main" allows tools: "exec/run"
  And the model queues exec__run with "echo hi"
  When the ACP client sends session/prompt
  Then the agent sends tool_call with kind execute
  And tool_call_update with status completed and kind execute

Scenario: an error tool result reports failed
  Given the model queues exec__run with "exit 3"
  When the ACP client sends session/prompt
  Then the agent sends tool_call_update with status failed

Scenario: a cancelled tool call uses a v1 status
  Given the model queues exec__run with "while :; do date; done"
  When the ACP client cancels while the tool runs
  Then the agent sends tool_call_update with status failed and content "cancelled"
  And response has stopReason cancelled

Scenario: every built-in tool has an explicit ToolKind
  Given isaac-agent's built-in tool list at the pinned sha
  Then each name maps to a ToolKind from the ACP v1 enum
```

- A new isaac-agent tool with no mapping fails a spec until it is mapped.
- Whether a result is an error comes from the tool result's own error flag
  when it has one. The `"Error:"` prefix is only a fallback:
  `echo 'Error: x'` reports completed.

## Working notes

- Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker,
  then verifier. The scenarios above are the behaviour to prove; write them
  as feature scenarios where the repo's existing steps can express them and
  as specs where they cannot, and say in the bean which is which.
- Reference work on zanebot (read-only): `/Users/zane/agents/zane-lab/experiments/acp-tool-status/` (`fix.v2.patch`, `REVIEW.md`) and `/Users/zane/agents/zane-lab/experiments/tool-error-flag/`. Read the experiment's
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
