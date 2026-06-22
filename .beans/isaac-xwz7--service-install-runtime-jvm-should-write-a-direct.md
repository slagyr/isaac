---
# isaac-xwz7
title: service install --runtime jvm should write a direct-clojure plist, not the bb trampoline
status: unverified
type: task
priority: normal
tags:
    - unverified
created_at: 2026-06-21T23:55:17Z
updated_at: 2026-06-22T00:15:00Z
parent: isaac-5zfv
blocking:
    - isaac-m2it
---

`isaac service install --runtime jvm` currently bakes the bb TRAMPOLINE into the plist (`isaac --root <root> server --runtime jvm`). At runtime the bb launcher stays RESIDENT and spawns+waits on a java child (`shell/exec!` = `@(process/process …)`, not execve). Two problems: (1) a needless resident bb babysitting java; (2) launchd's SIGTERM hits the bb parent, not the JVM — so the graceful-shutdown work in isaac-m2it can't fire its JVM shutdown hook.

## Decision (2026-06-21, Micah)
The service plist should invoke clojure DIRECTLY, using a fresh classpath generated each boot by the bb `isaac modules deps` command — via an sh wrapper so launchd (which can't do command substitution in ProgramArguments) gets a fresh classpath every start. `exec` collapses sh→clojure→java to a single JVM that launchd supervises directly, so SIGTERM reaches the JVM.

Chosen: sh wrapper + `--edn`/`-Sdeps` (NOT baked `--classpath`/`-Scp`). `modules deps --edn` is pure bb (just emits the deps map); `clojure -Sdeps` resolves it inside the one launch JVM (cached). Fresh classpath every boot picks up `:modules` sha changes with no reinstall — Micah explicitly prefers fresh over baked.

## Target ProgramArguments (--runtime jvm)
```xml
<array>
  <string>/bin/sh</string>
  <string>-c</string>
  <string>exec clojure -Sdeps "$(isaac --root <root> modules deps --edn)" -M -m isaac.main --root <root> server</string>
</array>
```
(--runtime bb unchanged: `isaac --root <root> server`.)

## Work
- `isaac.service.macos/install!` (+ cli.clj): for runtime jvm, emit the sh-wrapper ProgramArguments above instead of `server --runtime jvm`. Resolve <root> as today. Keep EnvironmentVariables PATH including the dirs holding isaac + clojure (`/usr/local/bin:/usr/bin:/bin`).
- Update macos_spec/cli_spec scenarios that assert the jvm plist contains `server --runtime jvm` -> assert the sh-wrapper form.
- Leave the interactive `isaac server --runtime jvm` trampoline (hzi0) in place for humans; only the SERVICE install changes.
- `service status` runtime detection: today it likely infers jvm from `--runtime jvm` in the plist; update to detect the sh-wrapper/clojure form.

## Acceptance
- `service install --runtime jvm` writes the sh-wrapper plist; `service status` reports runtime jvm.
- Booted service is a single `java` process with ppid launchd (no resident bb), serving the port.
- `service stop` SIGTERM reaches the JVM (paired with isaac-m2it's hook -> graceful goodbye).

## Status / notes
- Already HAND-APPLIED on zanebot (2026-06-21): plist rewritten to the sh-wrapper form, launchd bootout/bootstrap. Verified single `java` ppid=1, port 6674 bound, Discord gateway + imsg watcher up. The manual edit will be overwritten by the next `service install`; this bean makes it durable.
- Related/prereq for isaac-m2it (graceful shutdown only works on JVM once launchd supervises java directly).
- Implemented in isaac-server @ a3021d0.