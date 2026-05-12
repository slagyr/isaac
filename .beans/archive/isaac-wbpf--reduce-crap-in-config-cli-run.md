---
# isaac-wbpf
title: "Reduce CRAP in config CLI run"
status: completed
type: task
priority: normal
created_at: 2026-04-20T17:28:42Z
updated_at: 2026-04-20T19:11:02Z
---

## Description

Lower the CRAP score of isaac.config.cli.command/run below 10 by refactoring branching logic into smaller helpers while preserving behavior and maintaining test coverage.

## Notes

Refactored isaac.config.cli.command/run into a thin dispatcher with extracted subcommand helpers. CRAP for isaac.config.cli.command/run is now 2.0 (down from 438.7). Verification: bb spec, bb features, clj -M:crap.

