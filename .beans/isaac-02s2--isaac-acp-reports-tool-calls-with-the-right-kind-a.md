---
# isaac-02s2
title: isaac-acp reports tool calls with the right kind and status
status: completed
type: bug
priority: normal
created_at: 2026-11-17T15:14:41Z
updated_at: 2026-11-17T16:09:51Z
blocked_by:
    - isaac-07ls
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


## Exceptions

Authorized by the planner, 2026-11-17. These feature edits are part of this bean and are not tampering:

- `features/comm/acp/*.feature`: the scenario that never allowed exec and so only tested "unknown tool" is corrected to allow exec and assert the real kind and status. Scenarios that expect `completed` for a failed tool change to `failed`. List each one in the bean.
- Any feature file in this bean's repo: adding new scenarios, and new step definitions for them, that prove the acceptance above. No existing scenario may be removed or weakened beyond what is listed here.

## Worker implementation (2026-11-17, bean-isaac-02s2)

Done: isaac-agent raw tool-result error flag propagated to comm observers and persisted as explicit `:isError` for session/load replay. Agent squash landed on main at `5e46c797c385a1d72952332fa5e0453f43168677` (pin is reachable on origin/main). Agent `bb ci`: 984 examples, 0 failures, 1 pre-existing pending. No module version bump.

isaac-acp implementation on `bean/isaac-02s2` at `fbf8537` (not landed): wire-name → ACP v1 ToolKind mapping, failed/completed status based on result error flag with `Error:` prefix as fallback, replay using stored `:isError`, and cancelled in-flight tool call status `failed` with text `cancelled`. `bb ci` pinned at landed agent SHA: 109 specs, 76 features, zero failures. `ISAAC_GIT=1 ISAAC_TEST_TIMEOUT_MS=180000 bb jvm-spec`: 109 examples, 0 failures. `bb bean-gate verify isaac-02s2`: exit 2, not gated.

Acceptance proof: `features/comm/acp/tools.feature` scenarios “Tool call notifications include title, kind, and rawInput per ACP spec” and “tool result updates repeat title, kind, and rawInput for thin ACP clients” correct the formerly unknown-tool kind from `other` to `execute`; its Background now allows `exec/run`. New feature scenarios “an error tool result reports failed” and “successful exec output beginning Error: reports completed” exercise actual exec success/error through ACP. `features/comm/acp/cancel_tool_status.feature` changes the formerly `cancelled` status to v1 `failed`, asserts content `cancelled` and stopReason `cancelled`. No other existing feature scenarios changed. `spec/isaac/comm/acp_spec.clj` asserts every pinned isaac-agent built-in maps explicitly to a v1 ToolKind, legacy names, raw-result flag precedence and replay. `spec/isaac/comm/acp/server_spec.clj` asserts stored isError replay and in-flight cancel.

Next: verifier lands isaac-acp branch (already pinned to landed upstream main) and records its main SHA, then completes this ungated bean.



## Landed on main (2026-11-17)

main-sha: isaac-agent 5e46c797c385a1d72952332fa5e0453f43168677
main-sha: isaac-acp 0d5e88eb09baf7c3bc256bd017a394a4099541d9

Verification: clean isaac-acp branch fbf8537; bb ci 109 specs and 76 features, 0 failures; ISAAC_GIT=1 ISAAC_TEST_TIMEOUT_MS=180000 bb jvm-spec 109 examples, 0 failures. Authorized feature edits checked; agent pin reachable from origin/main. Historical test smells in acp_steps.clj/server_spec.clj (poll sleeps, timing, fixtures) predate this diff.
