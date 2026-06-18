---
# isaac-kjj0
title: Module-contributed comm does not activate on 'the Isaac process is started'
status: in-progress
type: bug
priority: normal
created_at: 2026-06-16T05:07:21Z
updated_at: 2026-06-18T16:55:29Z
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
