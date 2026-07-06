---
# isaac-7t1k
title: cli-server-spawned isaac subprocess does not see the fixture root's module surface
status: completed
type: bug
priority: high
created_at: 2026-07-06T23:26:27Z
updated_at: 2026-07-06T23:46:07Z
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

## Findings (2026-07-06, scrapper)

Root cause confirmed: module discovery for `:modules` local roots is cwd-sensitive. The spawned `/cli` subprocess forwarded `--root <fixture-root>` correctly, but cli-server launched `isaac` from the `isaac-cli-server` checkout. That made the fixture config's relative module coord `{:local/root "modules/isaac-acp"}` resolve against the cli-server checkout instead of the fixture root, so `isaac --root <fixture-root> modules` showed `isaac.comm.acp` invalid from that cwd and `isaac --root <fixture-root> acp` collapsed to `Unknown command: acp`.

Fix shipped in `isaac-cli-server` commit `00f5ec8`: when `/cli` argv carries `--root`, the spawned subprocess now runs with process `:dir` set to that explicit root. Verified locally:

- from fixture cwd, `isaac --root <fixture-root> modules` shows `isaac.comm.acp` ok
- from cli-server cwd, pre-fix it showed `isaac.comm.acp` invalid and `acp` unknown
- through `isaac.cli-server.dispatch/receive-line!` after the fix, `argv ["--root" <fixture-root> "modules"]` emits module rows with `isaac.comm.acp` ok
- same path with `argv ["--root" <fixture-root> "acp"]` now starts ACP (`stderr` frame `isaac acp ready`, exit 0 after stdin-close)

Unit + feature coverage added in `isaac-cli-server`:

- `bb spec`
- `bb features`
