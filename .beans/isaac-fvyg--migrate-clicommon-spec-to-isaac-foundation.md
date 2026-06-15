---
# isaac-fvyg
title: Migrate cli/common_spec to isaac-foundation
status: todo
type: task
priority: normal
created_at: 2026-06-15T16:20:01Z
updated_at: 2026-06-15T16:20:01Z
---

Restore the dropped isaac.cli.common-spec (2 examples) into isaac-foundation.

Baseline: isaac/spec/isaac/cli/common_spec.clj @ 09795481 (ns isaac.cli.common-spec, 2 it).
Source:   isaac-foundation/src/isaac/cli/common.clj  (NOT config/cli/common, which already has a spec)
Target:   isaac-foundation/spec/isaac/cli/common_spec.clj
Covers:   render-json — renders sets as sorted JSON arrays; prints rendered JSON to stdout.

Note: the stdout test must wrap output (with-out-str) to keep test output clean.
Acceptance (gate): file(it)==executed, 0 failures, zero '(it) inside (it)', 2 examples faithful.
