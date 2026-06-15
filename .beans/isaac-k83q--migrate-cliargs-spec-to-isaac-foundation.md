---
# isaac-k83q
title: Migrate cli/args_spec to isaac-foundation
status: in-progress
type: task
priority: normal
created_at: 2026-06-15T16:20:01Z
updated_at: 2026-06-15T16:24:38Z
---

Restore the dropped isaac.cli.args-spec (3 examples) into isaac-foundation.

Baseline: isaac/spec/isaac/cli/args_spec.clj @ 09795481 (ns isaac.cli.args-spec, 3 it).
Source:   isaac-foundation/src/isaac/cli/args.clj
Target:   isaac-foundation/spec/isaac/cli/args_spec.clj
Covers:   extract-root-flag strips --root <dir> / --root=<dir> / leaves args unchanged when absent.

Approach: same as the 5 completed moves. Port to current behavior; spec-only (flag real
source bugs, don't paper over). Foundation-owned root-flag parsing — should be clean, no agent vocab.

Acceptance (gate): bb spec -> file (it)==executed (no dead examples), 0 failures, SCRAP
structure-errors has zero '(it) inside (it)', assertions faithful to baseline (3 examples).
