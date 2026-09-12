---
# isaac-yrxx
title: Leg 5 — isaac-server's deps.edn drops isaac-agent at runtime; stale sibling pins are a CI check
status: completed
type: feature
priority: high
tags:
    - server
    - ci
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-12T15:49:49Z
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

## Implementation evidence (scrapper@isaac-work-1)

- Foundation: `bean/isaac-yrxx` @ `c041dccb70e4b0e20b96c15d64d52113486f8757` (base `origin/main@7a33619be220d50efb4b23a1213aa88c1985164b`). Implements `isaac modules pins`, real git fixture steps/SHA interpolation, untagged acceptance scenarios, and Foundation `bb pins` CI integration.
- Server: `bean/isaac-yrxx` @ `69106b14325c245df45b4409ee76d1c63f1d87e9` (base `origin/main@7f54ad9d2235ab0974a871142abc352c0216f1b8`). Removes Agent from top-level runtime deps, retains Agent under `:test`, and runs `bb pins` from CI.
- Final gates after rebase: Foundation focused feature 3 examples/6 assertions; `bb spec` 1019 examples/1841 assertions; `bb ci` 1019 specs and 184 features/489 assertions. Server `bb ci` 120 specs/246 assertions and 46 features/96 assertions using `ISAAC="../isaac-foundation-yrxx/libexec/isaac --root /tmp/yrxx-root"`; `clojure -Spath` confirms `isaac-agent` absent from runtime classpath.
- Resume point if verification returns: `src/isaac/modules/pins.clj:36`.



## Landed on main (2026-09-12)

main-sha: isaac-foundation 187356baed01e6e4aa3f7da313eb5be46e2cadbb
main-sha: isaac-server 1215832ac62bcf90ec3f56dad6a8a338652369e1

Squash-landed from sibling checkouts. Bean-tip trees match main trees.

Verifier gates (perceptor@isaac-verify) on bean/isaac-yrxx:
- foundation features/cli/modules_pins.feature: 3 examples, 0 failures, 6 assertions (@wip removed)
- foundation bb spec: 1019 examples, 0 failures, 1841 assertions
- foundation bb features: 184 examples, 0 failures, 489 assertions (clean gitlibs; a stale fixture-agent gitlib from work-1 caused one flake of modules_upgrade.feature:25 — isolated re-run 2/0 on bean and origin/main)
- foundation bb pins: exit 0 (no sibling pins in foundation deps)
- server clojure -Spath: no isaac-agent on runtime classpath; present under :test
- server bb spec: 120 examples, 0 failures, 246 assertions
- server bb features: 46 examples, 0 failures, 96 assertions
- One-time: Agent dropped from server top-level :deps; bb pins wired into foundation and server `bb ci`
