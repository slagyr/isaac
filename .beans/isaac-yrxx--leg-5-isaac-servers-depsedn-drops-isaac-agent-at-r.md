---
# isaac-yrxx
title: Leg 5 — isaac-server's deps.edn drops isaac-agent at runtime; stale sibling pins are a CI check
status: draft
type: feature
priority: high
tags:
    - server
    - ci
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-11T05:26:16Z
parent: isaac-3q4m
---

Parent: isaac-3q4m (decision 7). Depends on legs 2–4.

Repo: **isaac-server**, plus a CI rule shared by module repos.

Remove `io.github.slagyr/isaac-agent` from the server's top-level :deps (test aliases may keep it, like hooks). Add a CI check (bb task) in module repos that fails when a sibling pin in deps.edn is older than the registry's pin for that module — the 2026-09-11 fresh-box case (server pinned agent 0.1.46 while the registry said 0.1.66) becomes a red build, not a silent downgrade.

Scenarios to plan: server boots and serves HTTP with no agent on the classpath; the pin check fails on an older sibling sha and passes on an equal/newer one.
