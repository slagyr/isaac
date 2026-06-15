---
# isaac-kjjp
title: Restore activation.feature (lazy module activation) into isaac-agent
status: in-progress
type: task
priority: normal
created_at: 2026-06-15T20:41:04Z
updated_at: 2026-06-15T21:09:45Z
---

Baseline features/module/activation.feature @ 09795481 ("Module activation") — server-side LAZY activation:
module activated on first use of a capability, asserting :module/activated + :telly/started for a user comm
module (isaac.comm.telly), via the :comms table.

Target: isaac-agent (owns the comm berth, isaac.comm.telly, comm/registry slot-tree, and the lazy
module-loader/activate! call in comm/factory.clj). Exercise via agent's marigold comm fixtures + the real
comm slot-tree at runtime start.

WHY A NEW BEAN: this feature was re-homed into isaac-shnq AFTER shnq had already been verified + completed,
so it was never implemented — stranded. Splitting it out.

Confirmed a REAL gap: :module/activated-for-user-modules / activate-on-first-use has ZERO coverage in
server/agent/acp today. Restore the .feature + step-defs + helpers, remove @wip, green via bb features.

Relates to epic isaac-iiga (load vs start): activation is the LOAD side. Keep the test asserting
activation/load behavior; the start/stop service split is iiga's separate work.

Acceptance: activation.feature green under isaac-agent bb features, no @wip/pending, real comm slot-tree +
real fixture module (no faked steps)."
