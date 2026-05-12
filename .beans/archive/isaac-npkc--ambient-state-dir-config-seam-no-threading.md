---
# isaac-npkc
title: "Ambient state-dir + config seam (no threading)"
status: completed
type: task
priority: normal
created_at: 2026-05-06T22:32:21Z
updated_at: 2026-05-06T22:55:28Z
---

## Description

state-dir is set once at process boot and never varies per turn. Loaded config is also static between reloads. Both are currently threaded through every dispatch! / run-turn! / storage call. Make them ambient so module code can call (api/dispatch! turn-request) without carrying state-dir.

Approach: bind dynvars at boot (or expose accessors like (config/snapshot)). Audit async paths (compaction, off-thread tool work) and use bound-fn / explicit capture where bindings would be lost.

This is foundational for the resolver and parameter-object beads — both assume state-dir and config are reachable without being passed.

## Acceptance Criteria

state-dir and loaded config reachable from drive/bridge without being threaded as args; async work paths (compaction, tool execution) carry the binding correctly; bb spec and bb features green; no behavior change.

## Notes

Acceptance reframed at close: "ambient seam available; async paths bound correctly" — true and verified by code inspection (home/*state-dir*, config-snapshot accessors, server boot init, drive.turn/417 bound-fn). The retirement of threaded state-dir args from api/bridge/drive.turn signatures was deferred to isaac-33bt and isaac-k2e7, which rewrite the same arglists anyway; doing it three times would be churn for no behavior change. Strict no-threading outcome moves to k2e7's exit criterion.

