---
# isaac-daom
title: 'A Stop on a Claude Code turn does not return on Linux: three specs red in CI'
status: in-progress
type: bug
priority: critical
tags:
    - unverified
created_at: 2026-11-17T16:39:09Z
updated_at: 2026-11-17T16:47:18Z
---

Follow-up to isaac-55br, which landed on isaac-claude-code main as `84a444f`
with CI red. Repo: **isaac-claude-code**.

## What is wrong

`CI Tests` on main fails on Linux (GitHub runner) with 3 of 114 specs, all in
`spec/isaac/provider/claude_code/stop_accounting_spec.clj` at line 67, the
`(should-not (= ::timeout res))` in `cancel-after!`: a stopped turn did not
return within 3 seconds.

1. "a Stop is a cancelled result, not weather on a plain non-streamed turn"
2. "a Stop is a cancelled result, not weather before claude prints anything"
3. "a Stop carries what the API billed: a cancel after the result event keeps
   the finished request's usage"

The same suite passed on zanebot (macOS) for the worker and the verifier. The
previous main commit (`c0c8e3b`, isaac-xhb9) was green in CI.

So on Linux, in the paths where the turn is not streaming text when the Stop
arrives, the turn waits instead of returning at once. yopp runs Linux, so
this is a product question first and a test question second.

## To find

Whether the turn really blocks on Linux in those three paths (for example the
reader is blocked on the child's stdout and nothing closes it, the child or
its descendants are found differently on Linux, or the kill does not reach a
shell-wrapped script), or whether the spec's fake script behaves differently
under the runner's `sh`. Reproduce on Linux before changing anything: a
container on zanebot is fine, and CI on the bean branch is the final word.

## Acceptance

- All three examples pass on Linux and macOS, unchanged in what they assert.
  Do not lengthen the 3 second bound and do not mark anything pending or
  platform-specific.
- A Stop returns promptly on Linux in every path isaac-55br covers, by the
  same mechanism as on macOS wherever possible.
- `CI Tests` is green on the bean branch before handoff, and on main after
  landing. `bb ci` and `bb jvm-spec` pass locally. No module version bump.
- Children are still stopped by pid, one at a time. Never signal a process
  group; never use `pkill` or `killall`.
- The bean says in two lines what the cause was.

## Exceptions

Authorized by the planner, 2026-11-17: no existing feature scenario changes.
New scenarios or steps that prove the acceptance may be added.

## Implementation (2026-11-17)

Cause: On Linux, the shell's background `sleep` inherits stdout; terminating only the shell leaves the reader blocked until the child exits.
Fix: Terminate captured descendants by individual pid before terminating the parent, preserving the existing asynchronous reap.

`isaac-claude-code` bean branch commit `d731900`; no specs or feature scenarios changed.
Baseline CI on bean branch: run 44128436462 failed (same three timeouts); fixed CI run 44128936567 passed on Linux.
Local `bb ci` (including rebase run) and `bb jvm-spec` passed on macOS. `bb bean-gate verify isaac-daom` exited 2: no feature-baseline; handoff to verification for landing and main CI.
