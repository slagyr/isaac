---
# isaac-kjj0
title: Module-contributed comm does not activate on 'the Isaac process is started'
status: todo
type: bug
priority: normal
created_at: 2026-06-16T05:07:21Z
updated_at: 2026-06-16T05:13:26Z
---

Caught by features/module/comm_extension.feature "Multiple comm instances of the same :type coexist" (now @wip).

A comm declared via :modules (isaac.comm.telly) + :comms {:north-bot {:type :telly} :south-bot {...}} does NOT
activate when the Isaac process starts: the telly module's comm impl never registers, so the comm slot factory
(isaac.comm.factory/create!) can't build north-bot/south-bot. Only a stray cron :lifecycle/started fires; no
comm lifecycle entries appear.

Contrast: features/config/reconciler.feature passes ONLY because it manually registers the comm
("Given the telly comm is registered"). comm_extension specifically tests MODULE-contributed comm activation —
the broken path.

Likely module lazy-activation / load wiring (iiga territory: who activates a module-contributed comm slot at
process start) OR a regression from the CI-isolation/teardown work.

Fix: module-contributed comm impls must register + activate when the process starts (the :comms slot-tree
reconcile must trigger module load/activation for the impl). The scenario's assertion is already updated to the
canonical :lifecycle/started + path/impl form (matching reconciler.feature). Remove @wip when green.

Acceptance: comm_extension "Multiple comm instances" passes un-@wip'd; a module-declared comm activates on
process start.


## Scope: 3 scenarios @wip'd (same root cause)
- features/module/comm_extension.feature "Multiple comm instances of the same :type coexist"
- features/module/activation.feature "Activating the telly module on first comm slot use"
- features/module/activation.feature "Module activation failure surfaces a structured error"

The module-lifecycle events expected by these scenarios — :module/activated (loader), :telly/started /
:comm/activated (telly comm) — do NOT fire for a module-contributed comm; only the generic config-berth
:lifecycle/started (path/impl, no module/comm name) appears. So a module-declared comm reconciles as an inert
slot but the module-activation + comm-start lifecycle is broken. Fix all three together; check whether it's a
regression from the CI-isolation/teardown arc (974dee3e/58b8c1cb/03c448c4) — module activation may have lost
its event emission there.
