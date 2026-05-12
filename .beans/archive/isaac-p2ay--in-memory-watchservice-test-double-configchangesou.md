---
# isaac-p2ay
title: "In-memory WatchService test double + ConfigChangeSource seam"
status: completed
type: task
priority: normal
created_at: 2026-04-23T16:17:31Z
updated_at: 2026-04-23T17:51:27Z
---

## Description

Hot-reload tests need deterministic, synchronous file-change notifications. Introduce a ConfigChangeSource seam with two implementations:

- prod: wraps java.nio.file.WatchService; daemon thread pushes events into a channel
- test: synchronous — when a test writes via the in-memory fs, the corresponding ConfigChangeSource fires immediately before the write step returns

Plumb the seam into server startup so the reload loop is fed by ConfigChangeSource rather than directly by a WatchService. Production behavior unchanged at startup; tests get a deterministic trigger.

Also teach the existing 'the Isaac server is running' step (spec/isaac/features/steps/server.clj:138) to load config from disk via config/load-config when no in-memory crew/model atoms are injected, so real on-disk config is exercised.

No behavior change for existing features.

Acceptance:
1. ConfigChangeSource protocol (or equivalent seam) in src/isaac/config/
2. prod impl wraps java.nio.file.WatchService
3. test impl is synchronous and hooks into the mem-fs used by 'the file X exists with:'
4. the Isaac server is running boots with config/load-config when no injection atoms are present
5. bb features and bb spec stay green

## Notes

Completed with bb spec green. bb features still fails in unrelated areas outside this bead's scope; tracked separately in isaac-f88c.

