---
# isaac-ewal
title: JVM launch deps include foundation's own libraries
status: draft
type: task
priority: normal
tags:
    - foundation
created_at: 2026-10-15T15:48:17Z
updated_at: 2026-10-15T15:48:17Z
---

## Problem

`isaac modules deps --edn` (`isaac.foundation.module.loader/config->launch-deps`) emits every module coord plus foundation's source path, but none of foundation's own library deps (babashka/process, cheshire, clj-yaml, c3kit apron, tools.cli, tools.deps.alpha). Under bb those come from the keg's `libexec/bb.edn`, so the gap never showed.

So `isaac service install --runtime jvm` installs a service that cannot start: `Could not locate clojure/tools/gitlibs__init.class` from `isaac.foundation.modules.pins`. The brew keg ships no foundation `deps.edn`; only `libexec/bb.edn` lists the libraries.

## Workaround in place

On 2026-10-15 skiff's server was moved to the JVM with a hand-made launcher that merges the keg `bb.edn` `:deps` into the `modules deps --edn` map before `clojure -Sdeps … -M -m isaac.foundation.main server`. With the merge, all modules load and the server boots on the JVM. Once this bean lands, reinstall with `isaac service install --runtime jvm` and delete the launcher.

## Want

- `config->launch-deps` includes foundation's own `:deps` (one source of truth — read from what the keg ships, not a second hand-kept list).
- `isaac service install --runtime jvm` on a packaged install produces a service that boots.

## Planner notes

- Acceptance scenarios still to write (feature: the launch-deps map carries foundation's libraries alongside module coords).
- Related: `:paths` warning "Use of :paths external to the project has been deprecated" from the clojure CLI on every launch — worth a look while here.
