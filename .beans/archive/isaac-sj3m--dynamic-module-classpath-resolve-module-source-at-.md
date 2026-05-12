---
# isaac-sj3m
title: "Dynamic module classpath: resolve module source at activation time"
status: completed
type: task
priority: low
created_at: 2026-05-05T18:51:59Z
updated_at: 2026-05-05T19:12:14Z
---

## Description

Why: today, every module's src directory is statically listed in bb.edn :paths (and deps.edn). That means the JVM compile-time classpath has to know about every module up-front, which contradicts the whole point of dynamic discovery + lazy activation. Third-party module support (mx1d) is impossible in this model — a user can't drop a module into modules/ and have it just work.

## Scope

- isaac.module.loader/activate! adds the module's src/ directory to the runtime classpath BEFORE calling (require :entry-ns).
- Mechanism: pomegranate-style add-classpath!, or clojure.java.classpath manipulation, or a dynamic class loader scoped to the activation. Implementer chooses.
- bb.edn :paths and deps.edn :paths drop the per-module src entries; only "src" "spec" remain.
- features/modules/* scenarios (discovery, activation, schema_composition, discord) continue to pass.

## Why this matters

- Third-party modules: user drops modules/foo.bar/ into the state-dir, declares it in :modules, and Isaac activates it without a build-step or :paths edit.
- Module isolation: modules can't accidentally import each other's internal namespaces unless explicitly declared.
- Build hygiene: bb.edn stops growing one line per module.

## Open design questions (settle during implementation)

- Babashka compatibility: bb's classpath model differs from JVM Clojure. Test bb scrap/spec/features still work post-change.
- Reload semantics: if a module is reactivated after edits, does the classpath entry get refreshed?
- Failure mode: if the module's src/ is missing, do we hard-error at discovery (cccs) or activation (bnv0)?

## Acceptance

- bb.edn :paths is just ["src" "spec"] (or similar minimal set); no per-module entries.
- A new module dropped into modules/foo.bar/ with module.edn + src/ activates without any :paths edit.
- features/modules/* and existing comm/server scenarios continue to pass.
- bb scrap, bb spec, bb features all work without manual classpath setup.

## Acceptance Criteria

bb.edn :paths drops per-module entries; modules' src is added to classpath at activation time; all existing features and specs pass; new modules drop in without bb.edn edits

