---
# isaac-7t1k
title: cli-server-spawned isaac subprocess does not see the fixture root's module surface
status: in-progress
type: bug
priority: high
created_at: 2026-07-06T23:26:27Z
updated_at: 2026-07-06T23:28:17Z
blocking:
    - isaac-lcay
---


## Gap

Diagnosed by the isaac-lcay worker (2026-07-06, isaac-work-1). With isaac-gnji fixed, DIRECT invocation works: `isaac --root <fixture-root> acp` accepts ACP JSON-RPC, streams the fixture's grover echo model, and `isaac --root <fixture-root> modules` reports isaac.comm.acp ok. But the REAL remote path — `isaac remote ... -> cli-proxy -> cli-server -> spawned subprocess isaac --root <fixture-root> acp` — fails before ACP initialize: the spawned subprocess's stdout returns `Unknown command: acp` with a top-level command list that lacks acp entirely.

Same launcher, same fixture root, same forwarded --root: the cli-server-spawned subprocess resolves a DIFFERENT command/module surface than direct invocation. Something in cli-server's spawn (env, PATH, launcher selection, bootstrap, or working directory) prevents the module-gated command surface from loading the fixture root's modules.

## Impact

Blocks isaac-lcay's accepted proof (the real remote path is the point of that e2e). Beyond tests: any remote /cli usage that relies on module-contributed commands against a non-default root will silently see a reduced command surface.

## Planner decision (2026-07-06, Micah)

The proof seam is NOT relaxed — lcay proves the real path or nothing. This bean is the prerequisite, mirroring the isaac-gnji play. Suspect seam first: how cli-server builds the subprocess invocation (launcher path, environment, root forwarding into the module loader's bootstrap).
