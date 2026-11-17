---
# isaac-xhb9
title: Every cancel kills its own claude, and only its own
status: completed
type: bug
priority: high
created_at: 2026-11-17T15:14:56Z
updated_at: 2026-11-17T16:15:45Z
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


## Exceptions

Authorized by the planner, 2026-11-17. These feature edits are part of this bean and are not tampering:

- No existing scenario changes.
- Any feature file in this bean's repo: adding new scenarios, and new step definitions for them, that prove the acceptance above. No existing scenario may be removed or weakened beyond what is listed here.


## Implementation (2026-11-17)

isaac-claude-code bean/isaac-xhb9 at 169fa3c. Each invocation owns a run atom containing its process and termination state. The cancellation hook closes only that run; spawn-after-cancel immediately destroys the spawned process. Driver paths do not terminate a process after invocation finishes. No module version bump.

Acceptance: both behaviours are exercised with a real 8-second shell subprocess in features/claude_cancel.feature (two scenarios) and spec/isaac/provider/claude_code/claude_cli_cancel_spec.clj (two examples). The feature tests poll a marker proving each process started, cancel ~0.4 seconds later, assert per-cancel latency, exact termination log count and concurrent session isolation. Existing fake-CLI scenario remains unchanged. Red before fix: 2 examples, 2 failures; green after: bb ci (98 feature examples, 0 failures; specs green) and bb jvm-spec (76 JVM spec examples, 0 failures, 3 opt-in real pending; feature suite 67 examples, 0 failures).

Gate: bb bean-gate verify isaac-xhb9 exited 2, no feature-baseline. Implementation stays on bean branch pending independent verify.



## Verification failed (2026-11-17)

HEAD: isaac-claude-code 169fa3c192430a6228eaf0460b3b47999a86d777 (bean/isaac-xhb9); isaac beans 04ac44c5ac3a6194741226859f0c5040844e9e2e.
Working tree: clean (implementation detached review worktree and beans main before this note).

Reproduce: git worktree add --detach ../isaac-claude-code-xhb9-review origin/bean/isaac-xhb9; rg -n "Thread/sleep|\(def .*atom" spec/isaac/provider/claude_code/{cancel_steps,claude_cli_cancel_spec,cancel_support}.clj. New tests have unqualified Thread/sleep at cancel_steps.clj:18 and claude_cli_cancel_spec.clj:15, and top-level cross-test mutable fixture* atom at claude_cli_cancel_spec.clj:6. The project /verify test-quality gate requires an inline justification near each flagged pattern or a bean-documented file:line exception; neither exists. The 400ms delay is part of acceptance, but the test must document why sleeping rather than synchronizing is necessary. The fixture atom persists between examples and must likewise be justified or replaced with scoped state. cancel_support.clj:14 uses a bounded 1ms poll, justified by await-file! implementation; cancel_support.clj:28 writes to a created temporary directory. No existing feature scenarios modified; two new scenarios authorized by ## Exceptions, no @wip.

Fresh bb ci: 98 native specs, 0 failures, 3 opt-in pending; 76 JVM specs, 0 failures; bb jvm-spec: 67 features, 0 failures, 3 opt-in pending. Tests green, but test-quality gate blocks landing. Worker: add inline justifications for intentional sleeps and cross-test atom or refactor them, then rerun bb ci and bb jvm-spec. No implementation repo was landed.

## Verification repair (2026-11-17)

On isaac-claude-code bean/isaac-xhb9 at d19b4f9584cf453e673984b2e7b5b95f08d39cfb, replaced the cross-example fixture atom in claude_cli_cancel_spec.clj with Speclj `with` (per-example lazy fixture). Added inline explanations at claude_cli_cancel_spec.clj:13 and cancel_steps.clj:18: the bounded 400ms wait is the explicit acceptance interval after a subprocess-start marker, not an async synchronization wait. Real subprocess acceptance remains in both features and specs. bb ci: 98 native specs / 76 JVM specs, 0 failures; bb jvm-spec: 67 features, 0 failures (3 opt-in real pending). Gate exit 2: no feature-baseline; awaiting independent verify. No module version bump.



## Landed on main (2026-11-17)

main-sha: isaac-claude-code c0c8e3b014683fe46fa6332a42f78f986c0a4f27

Verified independently: bb ci (98 native specs, 76 JVM specs; zero failures), bb jvm-spec (67 features, zero failures, three opt-in real pending), both repeated on squash commit. Two new real-subprocess feature scenarios and two specs cover serial cancel and concurrent isolation; no existing feature scenarios changed. Test-quality repair documented intentional waits and scopes fixture per example. Dependency pins are reachable from upstream origin/main. Clean review and landed trees.
