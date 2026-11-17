---
# isaac-hqb6
title: Agent commands run in their own process group, apart from the server's
status: todo
type: bug
priority: critical
created_at: 2026-11-17T14:38:08Z
updated_at: 2026-11-17T14:39:03Z
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

feature-baseline: isaac-agent 8d8cc11e69971fa97684e7f967a98a71cebe6132
feature-blob: isaac-agent features/tool/built_in.feature c70864bc2f4752f8597bb5978ebf9b7bcc65ec63 198,205
