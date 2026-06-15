---
# isaac-fvyg
title: Migrate cli/common_spec to isaac-foundation
status: in-progress
type: task
priority: normal
created_at: 2026-06-15T16:20:01Z
updated_at: 2026-06-15T16:27:11Z
---

Restore the dropped isaac.cli.common-spec (2 examples) into isaac-foundation.

Baseline: isaac/spec/isaac/cli/common_spec.clj @ 09795481 (ns isaac.cli.common-spec, 2 it).
Source:   isaac-foundation/src/isaac/cli/common.clj  (NOT config/cli/common, which already has a spec)
Target:   isaac-foundation/spec/isaac/cli/common_spec.clj
Covers:   render-json — renders sets as sorted JSON arrays; prints rendered JSON to stdout.

Note: the stdout test must wrap output (with-out-str) to keep test output clean.
Acceptance (gate): file(it)==executed, 0 failures, zero '(it) inside (it)', 2 examples faithful.



## Verification failed

HEAD: cd8c01f4e0fc331a69b897d84f50ebcf960452fd
Working tree: clean

Target file [spec/isaac/cli/common_spec.clj](/Users/micahmartin/agents/verify/isaac-foundation/spec/isaac/cli/common_spec.clj:1) is missing from `isaac-foundation` `main`, so the migration was not actually delivered. `git rev-parse --verify HEAD^{tree}:spec/isaac/cli/common_spec.clj` fails, and `git log --all -- spec/isaac/cli/common_spec.clj` returns no commits for that path. I stopped before test execution because the acceptance gate `file(it)==executed` already fails at the file-presence step.
