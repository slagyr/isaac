---
# isaac-vs6f
title: 'Leg 1 — foundation owns the daemon: :isaac/subsystem berth + supervisor, process runner behind ''isaac server'', ''isaac service'' OS manager'
status: draft
type: feature
priority: high
tags:
    - foundation
    - server
    - subsystem
created_at: 2026-09-11T05:26:16Z
updated_at: 2026-09-11T05:26:16Z
parent: isaac-3q4m
---

Parent: isaac-3q4m (decisions 1–3).

Repo: **isaac-foundation** (receives), **isaac-server** (sheds).

Move, file for file where possible: isaac-server `src/isaac/service/{cli,manager,launch,macos,linux,protocol,registry,factory,runtime,supervisor}.clj` → foundation. The in-process half becomes `isaac.subsystem.*` behind a foundation-declared berth `:isaac/subsystem` with entries `{:id :rank :start :stop}` (rename of `:isaac.server/service`; clean cutover). The OS half keeps the `isaac service` command (launchd + systemd; the lqbc Linux work moves with it). The process runner (today isaac-server `app.clj` start!/stop!: load config → activate modules → start subsystems by rank → wait → stop in reverse) moves behind foundation's `isaac server` command; the HTTP listener becomes a subsystem entry contributed by isaac-server's manifest. Hail's optional-service-by-symbol special case disappears (it is an entry).

Scenarios to plan: subsystem start order by rank; stop in reverse on shutdown; a failing subsystem is supervised (isaac-royn); a module with no subsystems boots; `isaac service` features move unchanged (service.feature, service_linux.feature); the HTTP listener starts as a subsystem.

Blocks legs 2 and 4 (the berth must exist first).
