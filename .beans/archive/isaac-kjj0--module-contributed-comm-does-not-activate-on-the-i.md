---
# isaac-kjj0
title: Module-contributed comm does not activate on 'the Isaac process is started'
status: completed
type: bug
priority: normal
tags: []
created_at: 2026-06-16T05:07:21Z
updated_at: 2026-06-18T18:08:00Z
---

RE-SCOPED 2026-06-18 (planner): the original bean was written against a STALE
isaac-agent checkout. The named features were not deleted into the void — 95lv
(cc1ced2 "own comm reconcile door; server boot uses reconcile-modules!") MOVED
them to isaac-server, and f46df92 removed the agent copies. They live and run in
isaac-server now. DO NOT rebuild from scratch.

## Where the work actually is: isaac-server (NOT isaac-agent)

Both features exist, are NOT @wip, and fire on the post-95lv boot path
("When the Isaac server is started"):
• features/module/comm_extension.feature — "Multiple comm instances of the same
  :type coexist" → asserts :comm/activated north-bot + south-bot.
• features/module/activation.feature — 3 scenarios: telly activates on first
  comm-slot use (:module/activated + :telly/started); declared-but-unused module
  is NOT activated; activation failure surfaces :module/activation-failed.

These already cover the module-contributed path: user config has :comms
{:north-bot {:type :telly} ...}; the module supplies the :telly factory; boot
reconcile must activate the module to build the slot.

## This changes the framing

Activation is INTENTIONALLY lazy / on-first-use (activation.feature scenario 2
asserts a declared module is NOT activated when no slot uses it). So "does not
activate on load" is the wrong frame. Correct acceptance: a module-contributed
comm that IS configured in :comms activates at server start and emits the
module + comm lifecycle events.

## Do this, in order

1. VERIFY FIRST (likely already fixed by 95lv): run isaac-server's module
   features. The features are un-@wip and were kept green in CI (8b0ffe3), so
   they may already pass → if GREEN, kjj0 is resolved-by-95lv; close it
   (verifier confirms). The original agent-side @wip scenarios are obsolete.
2. If RED: the live bug is at SERVER BOOT reconcile (reconcile-modules! /
   berth reconcile in isaac-server/src/isaac/config/install.clj +
   isaac-foundation berths.clj). A module-contributed :comms slot must trigger
   module activation + emit :module/activated, :telly/started, :comm/activated.
   The tests already exist — fix the code, do not recreate scenarios.
3. INDEPENDENT of the bug (monolith-retirement cleanup, tie to e89r): the three
   scenarios hardcode :local/root "../isaac/modules/isaac.comm.telly" (the
   RETIRING monolith). telly now lives at ../isaac-agent/modules/isaac.comm.telly
   (server deps.edn :test already points there). Repoint the feature local-roots
   so they survive monolith deletion. This may be the only change actually
   needed.

## Note on a conflicting read

An exploration of berths/reconcile! suggested "boot doesn't discover module
comms." That describes the DEFAULT-INSTANCE case (a module declaring a comm
instance with no user :comms entry) — NOT this bug. kjj0 is about a USER-
configured :comms slot whose :type is module-supplied. The verify run in step 1
settles it empirically.

Acceptance (revised): isaac-server module features (comm_extension +
activation) pass with telly fixture repointed off the monolith; a module-
contributed comm configured under :comms activates at server start emitting
:module/activated + :comm/activated.



## RESOLVED (work-1, 2026-06-18) — tag: unverified

Verify-first run was RED (2 failures + 1 in reconciler.feature), not resolved-by-95lv. Root causes + fixes:

**1. Stale monolith telly path (step 3 of the bean).** Repointed every `../isaac/modules/isaac.comm.telly` → `../isaac-agent/modules/isaac.comm.telly`:
- features/module/activation.feature (3 slices), features/module/comm_extension.feature (1 slice).
- spec/isaac/configurator_steps.clj `telly-module-coord` — the legacy reconciler.feature fixture had the same dead path, so the module manifest could not be discovered, telly :extra-schema (:color/:loft/:mood) was never composed into the :comms schema, and :color was stripped from the slice → "Two comms run independently" got nil.

**2. Real activation bug (step 2): load-once `require` in isaac.comm.factory/ensure-impl!.** `create!` activates the module then `(require ns-sym)` to install the `create` defmethod — but a load-once require no-ops once the lib is in *loaded-libs*, even when the defmethod is absent (an earlier load that threw partway, e.g. a module failing first activation, or an ns removed without clearing *loaded-libs*). Result: `:module/activated` fired but `:comm/activated` never did (has-method? false). Fixed by `(require ns-sym :reload)` in the already-guarded method-missing branch — makes activation idempotent/recoverable, costs nothing once the impl is live. Confirmed necessary: path repoints alone leave comm_extension red.

**Result:** isaac-server full suite green locally — features 75/0, spec 121/0; module + config features 55/0. Diff: src/isaac/comm/factory.clj + the 3 path repoints above.

**Cannot confirm on CI:** isaac-server CI is currently red for an UNRELATED reason — classpath build fails, "Local lib isaac.comm.telly not found: .../isaac-agent/modules/isaac.comm.telly" (CI does not lay telly out at the deps.edn :test local-root). That is the e89r/CI-layout concern, not kjj0. Feature tests never run on CI until that is fixed.

**Flagged follow-ups (NOT done — out of kjj0 scope, no failing test):** the identical load-once `require` exists in isaac-server/src/isaac/service/factory.clj:30 (services berth) and isaac-agent/src/isaac/comm/factory.clj:52 (vestigial agent comm copy). Same latent unrecoverable-activation bug; planner may want a follow-up bean.

## Verification notes

- Verified on 2026-06-18 against the true GitHub `isaac-server` `main`, not the stale local `../plan/isaac-server` mirror that this verifier checkout had as `origin`.
- The delivered fix is present on GitHub `main`: [isaac-server/src/isaac/comm/factory.clj](/Users/micahmartin/agents/verify/isaac-server/src/isaac/comm/factory.clj:49) now reloads the entry namespace in the guarded method-missing branch with `(require ns-sym :reload)`, and the telly fixture coordinates are repointed to `../isaac-agent/modules/isaac.comm.telly` in [features/module/activation.feature](/Users/micahmartin/agents/verify/isaac-server/features/module/activation.feature:16), [features/module/comm_extension.feature](/Users/micahmartin/agents/verify/isaac-server/features/module/comm_extension.feature:20), and [spec/isaac/configurator_steps.clj](/Users/micahmartin/agents/verify/isaac-server/spec/isaac/configurator_steps.clj:23).
- Focused proof on fetched GitHub `main` passed: `env ISAAC_GIT=1 bb features features/module/activation.feature features/module/comm_extension.feature features/config/reconciler.feature` in `isaac-server` → `6 examples, 0 failures, 17 assertions`.
- The earlier verification failure was against local commit `76cf81d`, which was behind GitHub and did not contain the kjj0 fix.



## RE-HANDOFF (work-1, 2026-06-18) — verifier reviewed a STALE isaac-server checkout

The fix is on isaac-server origin/main at commit 03e8b2e (HEAD). Verified:
- `git rev-parse HEAD origin/main` → both 03e8b2ecdf33f16f758e429062889d6eb9548c0c
- `git show origin/main:src/isaac/comm/factory.clj` line ~49 has `(require ns-sym :reload)` + the explanatory comment, NOT plain `(require ns-sym)`.
- All three telly fixtures on origin/main point at `../isaac-agent/modules/isaac.comm.telly` (activation.feature:16/35/53, comm_extension.feature:20, configurator_steps.clj:24) — no `../isaac/modules/...` remain.
- Focused run on current main `ISAAC_GIT=1 clojure -M:test:features -f features/module/activation.feature -f features/module/comm_extension.feature -f features/config/reconciler.feature -t ~slow -t ~wip` → 0 failures.

The review findings (factory.clj:49 plain require; monolith paths in all three files) exactly match the PRE-fix commit 76cf81d, i.e. isaac-server before 03e8b2e. Please `git -C isaac-server pull` (or fetch + checkout origin/main → 03e8b2e) and re-run. Re-tagging unverified.
