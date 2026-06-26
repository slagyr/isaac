---
# isaac-qxvq
title: Cut foundation release with new naming strategies (unblock hail short-uuid deploy)
status: in-progress
type: task
priority: high
created_at: 2026-06-26T14:37:19Z
updated_at: 2026-06-26T14:47:15Z
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

## Progress (work-1, 2026-06-26)

### Done — foundation release (steps 1–2)
- isaac-foundation `src/isaac-manifest.edn` :version 0.1.7 → **0.1.8** (commit `93b9545`).
- **Unblocker:** main was RED (commit 778e91a's `step-tables-cell?` matched EDN set literals `#{…}` as step_tables DSL cells → cli_steps_spec failure). Fixed it (narrowed to real DSL cells `#*`/`#"…"`/`#name`) in both `spec/` and `spec-support/` copies, so the release commit is green. bb ci: spec 776/0, features 117/0; GitHub CI green on 93b9545.
- Tagged **v0.1.8** (frozen, → 93b9545), ran the Release workflow: GitHub Release v0.1.8 published; homebrew-tap `Formula/isaac.rb` auto-bumped to v0.1.8 (foundation-release tap job green).

### Pending — zanebot deploy (steps 3–4), live ops
- [ ] `brew upgrade` on zanebot so the bundled 0.1.8 seed has the strategies.
- [ ] Bump hail to b5f3db2 on zanebot; deploy (dry-boot + `(require 'isaac.hail.queue)` load test + config validate + restart); verify a sent hail gets a bare short-uuid.
These are live changes to the zanebot host (brew upgrade + service restart) — holding for confirmation before deploying.
