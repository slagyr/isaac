---
# isaac-k83q
title: Migrate cli/args_spec to isaac-foundation
status: completed
type: task
priority: normal
created_at: 2026-06-15T16:20:01Z
updated_at: 2026-06-15T16:34:54Z
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


## Implemented

Repo: isaac-foundation @ 4b71cf2
File: spec/isaac/cli/args_spec.clj (ns isaac.cli.args-spec)
Verify: cd isaac-foundation && bb spec spec/isaac/cli/args_spec.clj
Result: 3 examples, 0 failures, 3 assertions. Full foundation suite: spec 733/0, features 56/0. SCRAP: no (it)-in-(it) structure errors. Faithful port from isaac@09795481; behavior matches src/isaac/cli/args.clj. Spec-only.
