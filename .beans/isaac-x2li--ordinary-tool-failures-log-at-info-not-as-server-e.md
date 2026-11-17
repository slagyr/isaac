---
# isaac-x2li
title: Ordinary tool failures log at info, not as server errors
status: completed
type: bug
priority: normal
created_at: 2026-11-17T15:07:02Z
updated_at: 2026-11-17T15:18:34Z
---

Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker, then
verifier.

## Planner notes

- Start from the lab's reviewed patch on zanebot:
  `/Users/zane/agents/zane-lab/experiments/log-bug-hunt/beans/tool-error-result-not-server-error.v2.patch`. It was written
  against `isaac-agent 3233755`; main may have moved, so rebase by hand. Read the
  reviewer's `../REVIEW.md (one directory up)` in the same experiment first: it says what the first
  patch got wrong and what v2 changed. You own the result; the patch is a
  reference, not a contract.
- The lab's text below is its own draft. Where it and the code disagree,
  the code wins; say so in the bean.
- Never signal a process group or use `pkill`/`killall` in any check you
  run; the lab's `check.sh` scripts do, so do not run them as they are.
- `bb ci` and `bb jvm-spec` pass. No version bump; that happens at deploy.
- This changes `features/tool/execution.feature` on purpose: an ordinary
  tool failure (non-zero exit, path refusal, missing file, timeout) is no
  longer logged at error as `:tool/execute-failed`; it is logged at info as
  `:tool/error-result`. A crash inside a tool (an exception) stays at
  error as `:tool/execute-failed`. The planner confirms the scenario
  change; rewrite the affected scenarios to say exactly that and keep one
  scenario for each side of the line.

---

# Bean (draft): a tool's own error result is turn data, not a server :error

- Repo: isaac-agent (live: `3233755`, v0.1.97)
- Class: **wrong log level** (and a misleading event name)
- Patch: [`tool-error-result-not-server-error.patch`](tool-error-result-not-server-error.patch), proven by `../check.sh`

## What is wrong

`isaac.agent.tool.registry/run-handler-result` logs `:error
:tool/execute-failed` whenever a handler returns `{:isError true ...}`. That is
the tool doing its job: a command exited non-zero, a path was refused by
policy, an edit found no match. The model receives the error and recovers. The
same `:error :tool/execute-failed` is also logged for real faults (the handler
threw, returned nil, or the tool is unknown), so the scoreboard's #2 error key
cannot separate a broken server from `grep` finding nothing.

## Evidence (last 24 h, keys and coarse buckets only)

`error tool/execute-failed`: 458 total. Bucketed by the shape of the error
string (I did not read the text). Everything except the few "other" and
"spawn failed" rows is a recognised `{:isError true}` shape from
`exec.clj`/`fs_bounds.clj`/`edit`:

| tool | n | buckets |
|---|---|---|
| exec__run | 410 | 305 non-zero exit, 66 policy path refusal, 24 timeout, 11 sleep refused (policy), **2 spawn failed**, 2 other |
| fs__glob | 16 | 16 policy path refusal |
| fs__read | 8 | 6 policy path refusal, 2 not found |
| fs__edit | 7 | 6 no match, 1 policy path refusal |
| fs__grep | 6 | 5 policy path refusal, 1 other |
| subagent__spawn | 4 | 3 limit reached, 1 unknown model |
| session__read / session__list | 5 | bad time window |
| fs__multi_edit | 2 | no match |

Non-zero exit codes for exec__run: 1 (238), 2 (33), 128 (15), 124 and 143 (7 each).
These are ordinary test failures, `grep` misses and `git` refusals.

Also: an `:error` line carries the full `:arguments` (the command text) and
the tool's output in `:error`, at a level that is kept and shipped by default.
This patch moves those lines down a level but leaves the content the same. See risk.

## Fix (in the patch)

The `(:isError value)` branch logs `:info :tool/error-result` with the same
fields (`:tool :arguments :cwd :error`). `:error :tool/execute-failed` remains
for a thrown exception, a nil result, or an unknown tool. The value returned
to the caller is unchanged.

## Acceptance

```gherkin
Feature: Tool execution logging

  Scenario: A tool's own error result is logged at info with tool context
    When tool "fs__read" is executed with:
      | file_path | /no/such/path/that/exists |
    Then the tool result should indicate an error
    And the log has entries matching:
      | level | event              | tool     | arguments.file_path       |
      | :info | :tool/error-result | fs__read | /no/such/path/that/exists |
    And the log has no entries matching:
      | level  |
      | :error |

  Scenario: Nil tool result is treated as an error            # unchanged
    Given a tool "nil-tool" that returns nil is registered
    When tool "nil-tool" is executed with:
      | arg | value |
    Then the tool result should indicate an error
    And the log has entries matching:
      | level  | event                | tool     |
      | :error | :tool/execute-failed | nil-tool |
```

Specs (`registry_spec.clj`): isError gives one info `:tool/error-result` and
no `:error`, and the result reaches the caller unchanged. nil still gives
`:error :tool/execute-failed`, and so does a thrown exception (existing spec).

## Tests

| | spec | features |
|---|---|---|
| baseline @3233755 | 2018 ex, 0 fail | 973 ex, 0 fail, 1 pending |
| tests only (red) | registry 70 ex, **2 fail** | execution.feature 3 ex, **1 fail** |
| patched | **2020 ex, 0 fail** | **973 ex, 0 fail**, 1 pending |

## Risk / what it might make worse

- This deliberately changes a pinned contract: `execution.feature` said "Tool
  failure is logged at error". The planner should confirm the new level.
- Anything that alerts on `:tool/execute-failed` loses the 450/day ordinary
  cases. That is the point, but the scoreboard key changes meaning.
  `measure` would track `tool/error-result` separately.
- Two real faults hide in the isError bucket: `Cannot run program "/bin/sh"`
  (spawn failure, 2/day) and timeouts (24/day). A follow-up could have
  exec.clj mark those as `:fault true` and keep them at `:warn`. I left that
  out to keep this change to one decision.
- Privacy, not changed here: `:arguments` and tool output are still logged
  (now at info, where `:tool/start` already logs arguments at debug).
  Redaction is a separate bean.


## Implementation handoff (2026-11-17)

isaac-agent branch `bean/isaac-x2li` at `34b9c2c`. Applied and reviewed lab v2 against current main. Ordinary `:isError` results now log `:info :tool/error-result`; caught exec bugs marked `:fault` stay `:error :tool/execute-failed`, with the internal marker removed before returning to the caller. `IOException` on spawn remains an ordinary tool outcome. The feature distinguishes missing file from nil result. Tests: focused registry 70/0, fault-level 5/0, execution.feature 3/0; `bb ci` (2044 specs/0, 982 features/0, 1 pending) and `bb jvm-spec` (2044/0) pass. Gate exits 2: no feature-baseline.

The draft risk calls bad-workdir spawn refusal a hidden server fault and suggests keeping it at warn; review/code show this is an `IOException` tool outcome, not a server crash. Command timeout is also a tool outcome; registry-level timeouts already log warn separately. Other handlers that catch their own exceptions remain a follow-up; this change distinguishes caught exec faults only. No version bump.


## Exceptions

Authorized by the planner, 2026-11-17. These feature edits are part of this bean and are not tampering:

- `features/tool/execution.feature`: the scenario "Tool failure is logged at error with tool context" is rewritten so an ordinary failure (missing file, path refusal, non-zero exit, timeout) expects `:info` `:tool/error-result`; a scenario is kept or added in which a tool that throws expects `:error` `:tool/execute-failed`. The nil-result scenario stays at error.
- Any other feature in isaac-agent whose table expects `:error` `:tool/execute-failed` for an ordinary tool failure: that row changes to `:info` `:tool/error-result`, and nothing else in the scenario changes. List each one in the bean.
- Any feature file in this bean's repo: adding new scenarios, and new step definitions for them, that prove the acceptance above. No existing scenario may be removed or weakened beyond what is listed here.



## Landed on main (2026-11-17)

main-sha: isaac-agent 9c39e0c262cddbfc09970a1e76ea165bfbed9e2e

Verification: reviewed 34b9c2c against planner-approved scenario rewrite and lab v2; bb ci (2044 specs, 982 features, 0 failures, 1 unrelated pending), bb jvm-spec (2044/0), targeted execution.feature (3/0). Foundation pin edc1848 is reachable from origin/main. No @wip in execution.feature; no new test smells. Existing suite emits structured logs and CLI banners, not introduced by this diff. Historical Thread/sleep in registry_spec.clj:231 and tools_steps.clj:244,678; follow-up recommended. No .verify-baseline.edn exists; speed baseline not seeded in implementation repo (verifier read-only).
