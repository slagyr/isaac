---
# isaac-jndk
title: 'Verify gate: turn/compaction beans must run the FULL feature suite before pass'
status: draft
type: task
priority: high
created_at: 2026-08-31T16:27:46Z
updated_at: 2026-08-31T16:27:46Z
---

Repo: **isaac** (.toolbox verify skill/command docs) + possibly
~/.isaac/prompts skills on zanebot. Process bug, bitten twice in one week:

- isaac-p9zy: verify passed on targeted scenarios; merge left main with 18
  red compaction scenarios (isaac-x2up).
- isaac-x2up itself: verify passed on a 48-scenario targeted list; the
  merge left 3 residual reds (see the residue bean) that a full bb features
  would have caught.

Change: the verify skill/command instructs that any bean touching
drive/turn, tool_loop, compaction, session store, or comm emission runs the
FULL `bb features` + `bb spec` gate on the exact merge candidate (post-
rebase), not a targeted list; targeted lists are for iteration, never for
the pass. Also: verifiers must never commit unresolved conflict markers
(isaac-5nxf frontmatter incident, repaired 6cc378e4) — pull --rebase before
bean edits.

Draft until the exact doc edits are drafted (small; can ride with a docs
worker like isaac-qn1z did).
