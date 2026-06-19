---
# isaac-sme9
title: service install plist lacks PATH — launchd can't find bb (service won't start)
status: in-progress
type: bug
priority: high
tags:
    - in-progress
    - unverified
created_at: 2026-06-19T21:54:36Z
updated_at: 2026-06-19T21:57:55Z
---

Follow-up to isaac-r7z5. r7z5 fixed the plist ProgramArguments (packaged launcher
`isaac server`), but the generated plist STILL fails under launchd: the packaged
`isaac` wrapper invokes bare `bb`, and launchd runs with a minimal PATH that does
NOT include /usr/local/bin, so `bb` isn't found and the service exits / crash-
loops.

## Proven on zanebot (2026-06-19)

• `isaac service install` -> plist runs `/usr/local/bin/isaac --root ... server`.
• launchctl: no PID, exit 1. Not listening.
• Running the SAME command manually WITH `PATH=/usr/local/bin:$PATH` -> server
  boots, loads comms, listens on :6674 ("Isaac server running on 127.0.0.1:6674").
• Adding `EnvironmentVariables -> PATH = /usr/local/bin:/usr/bin:/bin` to the
  plist and reloading -> service comes up healthy (PID, exit 0, listening).
  (Workaround applied on zanebot.)

Note: `service install` even prints "Resolved bb: /usr/local/bin/bb" but does not
put it on the plist PATH.

## Fix (isaac-server src/isaac/service/macos.clj)

Add `EnvironmentVariables` -> `PATH` to the generated plist, covering at least:
• the resolved bb dir (/usr/local/bin), AND
• /usr/bin:/bin (git lives in /usr/bin — tools.deps needs it to resolve git-coord
  modules at classpath compose; the server proved it needs git).
Alternative: invoke bb by full resolved path in the wrapper. Prefer PATH (the
launcher shells out to bb AND git).

## Acceptance

• A fresh `isaac service install` -> `service start` brings the server up under
  launchd with NO manual plist edit: bb + git found, listens on :6674, stays up
  (KeepAlive).

## Relationships
• Follow-up to isaac-r7z5 (same `service install`, isaac-server). Completes the
  monolith->packaged service cutover (done on zanebot via the PATH workaround).

## Handoff notes (work-3)

• Plist now includes `EnvironmentVariables.PATH` = `<bb-dir>:<isaac-dir>:/usr/bin:/bin`.
• Packaged install resolves bb (required by launcher) and prints both paths.
• Regression: `service.feature` PATH scenario; `which` stub merges isaac+bb.
