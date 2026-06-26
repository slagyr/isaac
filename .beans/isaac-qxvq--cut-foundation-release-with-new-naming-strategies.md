---
# isaac-qxvq
title: Cut foundation release with new naming strategies (unblock hail short-uuid deploy)
status: in-progress
type: task
priority: high
tags:
    - unverified
created_at: 2026-06-26T14:37:19Z
updated_at: 2026-06-26T15:00:35Z
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
- [x] `brew upgrade isaac` on zanebot: 0.1.7 → 0.1.8 (seed now has the naming strategies).
- [x] Bumped hail `5a9989d` → `b5f3db2` in ~/.isaac/config/isaac.edn (backed up). Load-test gate `(require 'isaac.hail.queue)` against deployed deps = LOAD-OK (the var that failed before now resolves on the 0.1.8 seed); `config validate` OK; service restarted (pid 64759, running). Verified: a sent hail minted bare short-uuid id `0fb33893` (routed to undeliverable/0fb33893.edn — bare-id filename also confirms the stable-id lifecycle is live). Test record cleaned up.
These are live changes to the zanebot host (brew upgrade + service restart) — holding for confirmation before deploying.

## Summary of Changes (work-1, 2026-06-26)

DONE — both the release and the zanebot deploy.

**Release:** foundation `v0.1.8` (commit `93b9545`, tagged `v0.1.8`): :version 0.1.7→0.1.8 + fixed a blocking regression (778e91a's `step-tables-cell?` mis-routed EDN set literals to the DSL matcher). Release workflow published the GitHub release and auto-bumped homebrew-tap Formula/isaac.rb to v0.1.8.

**Deploy (zanebot):** `brew upgrade isaac` 0.1.7→0.1.8; hail pin → b5f3db2; the bean's `(require 'isaac.hail.queue)` load-test gate passed against the deployed deps (the exact `naming/->UuidStrategy` failure is gone); config valid; service restarted healthy; a sent hail mints a bare short-uuid id. Unblocks isaac-hoaq + isaac-3wic.

Verify SHAs: foundation origin/main has `93b9545` (v0.1.8); zanebot `isaac --version` = 0.1.8, config :modules isaac.hail :sha = b5f3db2.
