---
# isaac-4vt
title: "Per-crew filesystem boundaries: quarters + whitelist + cwd opt-in"
status: completed
type: feature
priority: high
created_at: 2026-04-16T20:30:09Z
updated_at: 2026-04-17T03:43:45Z
---

## Description

File tools (read, write, edit) can only access paths within the crew member's quarters and explicitly whitelisted directories. Operations outside these boundaries are rejected at the tool level.

## Allowed paths per crew member
1. Crew quarters (~/.isaac/crew/<id>/) — always included
2. tools.directories — explicit whitelist from config
3. Session cwd — only when the crew opts in via :cwd token in tools.directories

## Config (new map-by-id shape — depends on isaac-16v)
{:crew {:marvin {:model :llama
                 :tools {:allow       [:read :write :edit :exec]
                         :directories [:cwd "/tmp/isaac-playground"]}}}}

## Design
- Default deny: no filesystem access without tool allowlist (isaac-mrn)
- Crew quarters (~/.isaac/crew/<id>/) is always allowed — personal persistent storage
- tools.directories is an explicit whitelist; accepts absolute paths and the :cwd token
- :cwd token expands to the session cwd at tool invocation time
- No implicit cwd access — crew must opt in. Prevents scope-creep when crew joins a session.
- ~/.isaac/config/ is NEVER in any boundary — crew cannot read/write their own config
- Path rejection uses canonical-path check to defeat traversal (../)

## Feature scenarios
features/tools/filesystem_boundaries.feature — 8 scenarios:
1. read in own quarters (happy path)
2. read in whitelisted directories
3. cannot read outside boundaries (+ error message)
4. cannot write outside boundaries (+ error message)
5. cwd access requires :cwd opt-in
6. without :cwd opt-in cannot access cwd (counter-test)
7. path traversal rejected
8. cannot read own config file (security teeth)

Feature: features/tools/filesystem_boundaries.feature
Depends on: isaac-mrn (tool allowlist), isaac-16v (config shape + .isaac/config/ layout)

## Acceptance Criteria

1. Remove @wip from all scenarios in features/tools/filesystem_boundaries.feature
2. bb features features/tools/filesystem_boundaries.feature passes (8 examples, 0 failures)
3. bb features passes (no regression)
4. bb spec passes
5. Crew quarters directory auto-created on first use
6. Error messages for boundary violations include the text 'path outside allowed directories' so operators can diagnose

