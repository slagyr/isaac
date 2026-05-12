---
# isaac-dps
title: "Dual-runtime server architecture (bb-first, JVM effects)"
status: draft
type: feature
priority: normal
tags:
    - "deferred"
created_at: 2026-04-08T16:02:08Z
updated_at: 2026-04-08T16:02:13Z
---

## Description

Epic: support both Babashka and JVM Clojure runtimes for the server, with a bb-first implementation style and explicit JVM-only effect adapters.

## Direction
- Prefer Babashka-compatible implementations first
- Isolate JVM-only effects behind dedicated adapter namespaces
- Refactor shared logic into .cljc when it is genuinely shared
- Run shared specs on both runtimes

## Goals
- Fast/snappy dev workflow in bb
- Ability to use long-running JVM server optimizations when needed
- Most application logic remains runtime-agnostic
- Runtime differences are explicit and localized

## Architectural guidance
- Shared domain/orchestration logic in cljc where appropriate
- Dedicated bb and jvm runtime adapters at the edges
- Avoid scattering reader conditionals through business logic
- Treat JVM-only libraries as effects, not core dependencies

## Likely follow-on work
- Server bootstrap for bb
- Server bootstrap for jvm
- Runtime abstraction/effects boundaries
- Dual-runtime test execution for shared namespaces
- Channel/server features built on shared runtime-agnostic core

## Notes
- This is an epic/planning bead and should not be worked directly as a ready bead
- Child beads should link back to this epic as the architecture umbrella

