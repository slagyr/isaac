---
# isaac-uz44
title: "Update tests for turn/run-turn! rename"
status: completed
type: task
priority: normal
created_at: 2026-04-28T16:03:49Z
updated_at: 2026-04-28T18:05:37Z
---

## Description

The broader spec and feature suites now fail outside isaac-dubm because several specs and gherclj steps still reference isaac.drive.turn/process-user-input!, but the implementation now exposes run-turn!. Update the affected specs and feature steps to use the current public entry point so bb spec and bb features are green again.

