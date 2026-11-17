---
# isaac-hqb6
title: Agent commands run in their own process group, apart from the server's
status: todo
type: bug
priority: critical
created_at: 2026-11-17T14:38:08Z
updated_at: 2026-11-17T14:42:50Z
---

Found 2026-11-17. Zane Lab killed the live Isaac server on zanebot twice in
one night (05:09:29Z and 05:32:33Z on 2026-11-17) and lost 46% of a week of
subscription capacity.

## What is wrong

`exec__run` (isaac-agent `tool/exec.clj`, `start-process`) starts
`/bin/sh -c <command>` with a plain `ProcessBuilder`. The child inherits the
server's process group, and the server is that group's leader (on zanebot
pid 84137 had pgid 84137). So any agent command that signals "its" group
signals the server:

- `kill -TERM -- -84137` (a lab child meant to stop a test suite it believed
  it had put in its own group; the group it read back was the server's);
- `kill -TERM -- -$pg` where `$pg` came from `ps -o pgid=` on a process that
  had never left the server's group.

`kill 0` from any agent command does the same. The server took the SIGTERM,
suspended every turn and exited; launchd restarted it.

A second effect of the same cause: on a timeout or cancel `destroy-process!`
calls `.destroy` on the `sh` only. Anything the command started in the
background keeps running after the tool returns "timeout exceeded".

## Design

- Every `exec__run` command runs in a process group of its own, so its pgid
  is never the server's. No `setsid` on macOS; the same code must work on
  macOS (zanebot) and Linux (yopp), under bb and under the JVM. A
  `perl -e 'setpgrp(0,0); exec @ARGV'` launcher is one way; pick what is
  smallest and has no new dependency a stock macOS or Debian lacks.
- Timeout and cancel signal the command's whole group (TERM, then KILL after
  the existing grace), so background children stop with it.
- Output capture, exit codes, workdir, forwarded env and the fence on
  directories are unchanged.

## Not in this bean

- The Claude Code CLI children the driver spawns also sit in the server's
  group. They run no agent-written commands, so they are left alone here.
- The labs' own rule against group kills (already in both cron prompts).

## Acceptance

- The two `@wip` scenarios baselined below pass with `@wip` removed, both in
  `features/tool/built_in.feature`: a command's process group is not the
  group of the process running the tool; a timed-out command's background
  child is gone when the tool returns.
- Two new steps, and only these two:
  - `the tool result is not this process's group id`: the trimmed tool
    result is a number and differs from the process group id of the process
    running the scenario (`ps -o pgid= -p <own pid>`).
  - `the process recorded in file "<name>" is not running`: read a pid from
    that file in the clean test directory (the same place
    `the file "<name>" has content` reads from) and assert no such process
    exists, polling for up to 2 seconds.
- Every existing exec scenario passes unchanged (`built_in`, `timeout`,
  `exec_forward_env`, `bridge/cancel`, `bridge/suspend`, `directories`).
- `bb ci` and `bb jvm-spec` pass. No version bump; that happens at deploy.
- If a baselined step cannot do what a scenario asks of it, stop and say
  which step; do not bend the production code to fit.


## Contract conflict (2026-11-17)

The second baselined scenario cannot satisfy its mandated step `the process recorded in file "bg.pid" is not running` without changing the scenario or weakening the step. The scenario runs `sleep 300 & echo $! > bg.pid; wait` through `exec__run` with **no workdir and no session**. `tool-called` supplies only `state_dir`, and `exec-tool`'s `resolve-exec-args` leaves workdir nil when there is no session cwd; `ProcessBuilder` therefore writes `bg.pid` to the runner's `user.dir` (the repository root). But the acceptance requires the step to read the pid from the clean test directory, `target/test-state-tools/` (the same path `file-has-content` resolves from `:root`). Reproduced on native bb: `bg.pid` appeared in repo root, while `target/test-state-tools/bg.pid` did not exist. The unimplemented step cannot legitimately read that file. Temporarily removing @wip and adding the two exact steps yielded first scenario RED (same PGID) and second scenario failing before its assertion. Restored all temporary edits and removed the stray pid file; no implementation was shipped. Planner needs to amend/rebaseline the scenario to set `workdir` to the clean test directory (or create a session there), then dispatch again. No production cwd change solely to make this scenario pass.

## Planner decision (2026-11-17, for isaac-hqb6, timeout scenario workdir)

My slip: with no workdir and no session the command runs in the repository
root, not the clean test directory. The second scenario is re-cut on
isaac-agent main (`dcf513c`) and re-baselined below: it creates `bgdir` under
the test root, runs with `workdir bgdir`, and the step reads
`bgdir/bg.pid` from the clean test directory. The step's meaning is
unchanged. No production cwd change. The first scenario is untouched.

feature-baseline: isaac-agent dcf513c6f24ce2471551daf9b00f33e11f93b58a
feature-blob: isaac-agent features/tool/built_in.feature 19ddfff4aa07d47ba5215648fcd9a5a9c39e579c 198,205
