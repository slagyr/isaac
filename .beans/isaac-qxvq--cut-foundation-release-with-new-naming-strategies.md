---
# isaac-qxvq
title: Cut foundation release with new naming strategies (unblock hail short-uuid deploy)
status: todo
type: task
priority: high
created_at: 2026-06-26T14:37:19Z
updated_at: 2026-06-26T14:37:19Z
---

Foundation main has UuidStrategy + ShortUuidStrategy (isaac.naming, from isaac-a3fb) but the manifest :version is still "0.1.7" — and zanebot runs the bundled foundation 0.1.7 brew SEED (seed-authoritative). The seed lacks the strategies, so any module that uses them cannot deploy to zanebot.

## Observed (2026-06-26)
Deploying hail b5f3db2 (isaac-hoaq short-uuid + isaac-3wic configurable naming) to zanebot FAILED the namespace-load test:
  Syntax error compiling at (isaac/hail/queue.clj:44:17). No such var: naming/->UuidStrategy
(the --version dry-boot did NOT catch it — queue.clj loads lazily; the explicit `(require 'isaac.hail.queue)` against the deployed deps is what surfaced it.) Hail was reverted; agent/server/discord deployed (incl the igs4 heartbeat fix). Hail is HELD at 5a9989d on zanebot.

## Fix
Cut a real foundation release that includes the naming strategies:
1. Bump isaac-foundation src/isaac-manifest.edn :version (0.1.7 -> 0.1.8).
2. Run the release (the i0g9 pipeline auto-bumps the homebrew tap).
3. `brew upgrade isaac-foundation` (or isaac) on zanebot so the bundled seed has the strategies.
4. Then bump hail to b5f3db2 on zanebot and deploy (dry-boot + the `(require 'isaac.hail.queue)` load test + config validate + restart). Verify a sent hail gets a bare short-uuid id.

## Broader note
Foundation main has drifted ahead of the released 0.1.7 seed without a version bump. Any module change depending on new foundation code is blocked the same way. Worth a habit: bump foundation version + release whenever foundation gains code that modules depend on. Verify deployability with `(require 'the.module.ns)` against the deployed deps, not just `--version` (lazy load hides unresolved vars).

## Blocks
- isaac-hoaq (switch hail to bare short-uuid) deploy.
- isaac-3wic (configurable hail naming strategy) deploy.

Surfaced 2026-06-26, Micah deploy request.
