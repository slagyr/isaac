---
# isaac-91xb
title: Feature suites share /tmp/isaac, so concurrent sessions fail each other's runs
status: todo
type: bug
priority: normal
tags:
    - test
created_at: 2026-09-21T17:59:49Z
updated_at: 2026-09-21T17:59:49Z
---

Repo: **isaac-agent** (and any feature that names an absolute test root).

`features/config/schema_cli_options.feature` starts every scenario with

    Given an empty Isaac root at "/tmp/isaac"

That path is absolute and shared by every checkout on the machine. Isaac's
crew run several sessions at once, each with its own worktree, and `bb ci` in
two of them overlaps for minutes at a time — so one suite wipes and rewrites
`/tmp/isaac` while another is reading it.

## What it looks like when it bites

Seen 2026-09-21 while landing isaac-xpkf, with 6–8 concurrent `bb features`
processes on the box. Three full runs of the same tree, no code change between
them:

    843 examples, 0 failures        <- clean window
    843 examples, 2 failures
    843 examples, 75 failures

Every failure was in that one feature file, and the loudest shape was

    config-schema collision at :comms [:schema :value-spec :dynamic-schema :berth]:
      :isaac.agent/comm vs :isaac.server/comm

— a module manifest from *another repo's* suite, discovered out of the shared
root mid-run. Others were quieter: `options:.*telly` simply missing. Run the
file alone and it is 7/0, three times in a row, while the other suites keep
running.

The cost is not the red: it is that a worker cannot tell an environment flake
from a real regression without re-running the file in isolation, and a CI-style
"suites are green" claim is only true of the window it ran in.

## Work

Give each run its own root. The step already supports a relative root
(`target/...` per checkout) which the rest of the suite uses; `/tmp/isaac`
appears to be the only absolute one. Either point these scenarios at a
per-checkout `target/` root, or make the "empty Isaac root at" step tilde/temp
expand into a unique directory per run.

Then sweep for others: `grep -rn '/tmp/' features/` across the isaac repos —
anything absolute is the same trap.

## Acceptance

- no feature in isaac-agent names an absolute shared path as its Isaac root
- `features/config/schema_cli_options.feature` passes while a second full
  `bb features` runs concurrently in a sibling worktree
- the scenarios keep asserting what they assert today (module discovery,
  provenance prefixes) — this is isolation, not a rewrite
