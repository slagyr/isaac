---
# isaac-yrxx
title: Leg 5 — isaac-server's deps.edn drops isaac-agent at runtime; stale sibling pins are a CI check
status: in-progress
type: feature
priority: high
tags:
    - server
    - ci
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-12T14:36:35Z
parent: isaac-3q4m
blocked_by:
    - isaac-jrj0
    - isaac-q9j6
    - isaac-zgfx
---

Parent: isaac-3q4m (decision 7). Depends on legs 2–4.

Repo: **isaac-server**, plus a CI rule shared by module repos.

Remove `io.github.slagyr/isaac-agent` from the server's top-level :deps (test aliases may keep it, like hooks). Add a CI check (bb task) in module repos that fails when a sibling pin in deps.edn is older than the registry's pin for that module — the 2026-09-11 fresh-box case (server pinned agent 0.1.46 while the registry said 0.1.66) becomes a red build, not a silent downgrade.

## Acceptance

- isaac-foundation 80c6c1e: `features/cli/modules_pins.feature` (3 scenarios, @wip): pinned at an ancestor of the registry sha → exit 1, stderr names module, pinned sha, registry sha, "older"; equal → exit 0 "current"; ahead → exit 0 "ahead". Ancestry via the gitlib clones (tools.gitlibs); `:module-registry` may be a local file. NEW steps: `a git repository {name} with commits:` (table) and `the git repository {name} gains a commit {msg}` — real git in the test dir; `{sha of "<msg>"}` placeholders resolve from it.
- Dropping `io.github.slagyr/isaac-agent` from isaac-server`s top-level :deps and adding `bb pins` (calls `isaac modules pins`) to the module repos` CI workflows are one-time acceptance checks, not scenarios.

```
cd isaac-foundation && bb features features/cli/modules_pins.feature && bb spec && bb ci
cd isaac-server && bb ci   # with no agent on the runtime classpath
```

## Work checkpoint (scrapper@isaac-work-1)

RED: `bb features features/cli/modules_pins.feature` runs all three untagged scenarios and currently reports 3 failures. Real git fixture steps and SHA interpolation exist; production pins classification/CLI dispatch exists, but acceptance output is empty because the in-memory `deps.edn` fixture is root-relative while command discovery is still using the process cwd. Next: align module-repository cwd for this command, then rerun the focused feature. Resume at `src/isaac/modules/pins.clj:16` and `spec-support/src/isaac/foundation/fs_steps.clj:159`.
