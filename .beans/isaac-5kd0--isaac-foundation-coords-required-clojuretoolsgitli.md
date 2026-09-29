---
# isaac-5kd0
title: 'isaac-foundation: coords required clojure.tools.gitlibs, which the server''s JVM classpath lacks (isaac-zr75 regression) — hotfixed'
status: completed
type: bug
priority: critical
created_at: 2026-09-29T18:42:29Z
updated_at: 2026-09-29T18:42:29Z
---

2026-09-29: foundation b83ff93 on zanebot failed to boot the server (`Could not locate clojure/tools/gitlibs` in `isaac.module.coords`). isaac-zr75 (3a199d9) made coords require `clojure.tools.gitlibs` for `cache-dir`, but only the bb test setup has that library; the server's JVM classpath is foundation src + module deps. Outage ~30 min.

Hotfix (planner, Micah approved option B): coords resolves the cache dir inline the way gitlibs does (GITLIBS env → clojure.gitlibs.dir property → ~/.gitlibs); spec updated to set the property. 1298 specs / 235 features green.

main-sha: isaac-foundation 95e44ce

Follow-up worth a bean: a foundation feature that boots the packaged server entry point, so a runtime-classpath gap fails CI.
