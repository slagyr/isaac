---
# isaac-u1oe
title: Server survives a JDK upgrade on disk (spawn helper mismatch)
status: draft
type: bug
priority: normal
created_at: 2026-11-11T15:08:06Z
updated_at: 2026-11-11T15:08:06Z
---

## Problem

On Linux the JVM starts child processes through `jspawnhelper`, a file on
disk in the JDK install. When the OS package manager upgrades the JDK under a
running server (unattended upgrades do this), the running JVM finds a
`jspawnhelper` from a different version, and every `ProcessBuilder.start`
fails with `Failed to exec spawn helper … JDK version mismatch`. Isaac spawns
the claude CLI, MCP servers, shell tools, and so on, so the server stays up
but can do nothing until someone restarts it. Seen live 2026-11-11: an
openjdk-21 point release broke a server that had been up for 6 days, and it
stayed broken for 8.5 hours until a manual restart.

## Goal

A JDK upgrade on disk does not silently break a running server.

Options to settle in planning:

1. **Avoid the helper.** Start the server JVM with
   `-Djdk.lang.Process.launchMechanism=FORK` on Linux, so spawning doesn't
   depend on the on-disk `jspawnhelper`. Trade-off: fork (copy-on-write)
   instead of posix_spawn. Check that it is still supported on the JDK
   versions we ship on (21+).
2. **Detect and say so.** Recognize the spawn-helper error (or check
   `/proc/self/exe` for "(deleted)") and raise one clear attention message:
   "the Java runtime was upgraded under the server; restart required". Also
   show it in `/health`.
3. Both.

The recommendation is (3): (1) prevents the failure, (2) covers any other
on-disk runtime swap.

## Acceptance (to be written as @wip scenarios before baseline)

- The server launcher on Linux passes the chosen launch mechanism (or the
  decision not to is recorded here).
- When a process spawn fails with the spawn-helper error, the server posts
  one attention message naming the cause and the fix (a restart), not one per
  turn, and `/health` reports it.

## Likely repo scope

isaac-foundation (server launcher / JVM opts), plus wherever spawn failures
surface (isaac-agent). Host-side mitigation (holding JDK packages out of
unattended upgrades) is ops, not code; it doesn't belong in this bean.

## Related

isaac-y3wm — the claude-code provider labels spawn failures as
`:mcp-unavailable`.
