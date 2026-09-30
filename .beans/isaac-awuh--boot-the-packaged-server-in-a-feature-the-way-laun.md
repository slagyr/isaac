---
# isaac-awuh
title: Boot the packaged server in a feature, the way launchd does
status: draft
type: task
created_at: 2026-09-30T03:01:46Z
updated_at: 2026-09-30T03:01:46Z
---

Likely repo: **isaac-foundation**. Follow-up to isaac-5kd0 / isaac-oc3f.

## Why

Two zanebot outages shipped through green suites because no test starts the
server the way production does:

- 2026-09-29 (isaac-5kd0, ~30 min): foundation b83ff93 made
  `isaac.module.coords` require `clojure.tools.gitlibs`. bb (CLI, features,
  rehearsal) has it built in; the launchd server's JVM classpath does not.
  Every suite green, `isaac server` could not boot.
- 2026-09-11 (isaac-oc3f): the unit's `isaac server` booted through a
  different entry point that skipped `app/start!`; the port answered 401 while
  the resume scan, delivery workers and hail binding never ran.

Features call `main/run` / `app/start!` in-process; the launcher
(`isaac.launcher` → `isaac.main`, JVM, production classpath) is never
exercised.

## Idea

One feature (tag `@slow` if needed, and make sure CI runs it) that:

- boots the packaged server exactly as the launchd unit does: the `isaac`
  launcher, `isaac server`, on the JVM with only the production classpath
  (no spec/dev aliases), against a temp ISAAC_ROOT with a minimal config;
- waits for `:server/started` and `:resume/scan-complete` in the server log
  and an HTTP answer on the configured port;
- checks the expected components registered (not just "port answers");
- stops the process.

## Open questions (planner)

- Where the packaged launcher lives for a test to call (brew keg layout vs
  `libexec/isaac` in the repo) and how CI provides a JVM + deps cache.
- Which module set: foundation alone, or foundation + a pinned module list
  (the 09-29 break only showed with modules loaded)?
- Step reuse: the "Isaac runner is started" steps boot in-process; this needs
  a subprocess step.

Draft until scenarios are written.
