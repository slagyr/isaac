---
# isaac-9bno
title: 'Suite health (isaac-hail): features drift against current agent — 8 pre-existing failures, 5 order-dependent under 0.1.62 memory-store hydration'
status: todo
type: bug
priority: high
created_at: 2026-09-11T01:19:09Z
updated_at: 2026-09-11T01:19:09Z
---

Repo: isaac-hail (+ agent test infra if the leak is there). isaac-hail CI runs against its pinned agent (0.1.52) and has been green while the deployed agent moved to 0.1.6x; against the live agent the features are red. Measured 2026-09-11 with `clojure -M:dev-local:features` (local ../isaac-agent):

- agent 0.1.58 and 0.1.61: 146 examples, 8 failures — config validate scenarios (bands.feature: accepts data map/rejects non-map; rejects invalid :reach; rejects :crew as seq; schema-checks frontmatter band), band-inheritance 'A base cycle is a clear error' + 'A missing base reference is a clear error' (these two fail with the file run alone), delivery 'cancelling a live hail turn archives to hail/cancelled', resume 'a cancelled hail marker is archived, not re-queued'.
- agent 0.1.62 (b6w0): 13 failures — the 8 above plus 5 that pass alone and fail only in the full run: band-inheritance 'child inherits session-tags and data', 'child data key overrides', 'child without a body inherits the base body', 'Base chains resolve transitively'; band-templating 'Band body is a template rendered with params', 'Sending a hail to a templated band returns the id and creates a record', 'hail_get retrieves a prior templated hail'. Suspect: b6w0's MemorySessionStore hydrates sessions from disk (ensure-hydrated!/scan-session-dirs), so sessions written by earlier scenarios under a reused feature root revive in later ones and capture tag-routed hails.

## Required
1. Pin agent 0.1.62+ in isaac-hail deps.edn/bb.edn (the deployed one) and make the features green against it.
2. For the 8: decide per scenario whether the contract moved (recut the scenario, cite the agent change) or hail regressed (fix). Config-validate rejections that no longer reject are product bugs.
3. For the 5: prove the leak (run the failing file immediately after its predecessor), then fix at the fixture (fresh root per scenario, or reset the store) — if the leak is in the agent's shared steps/memory store, fix it in isaac-agent on a bean branch and note it here.

## Acceptance
`ISAAC_GIT=1 clojure -M:features` and `clojure -M:spec` in isaac-hail: 0 failures against the pinned current agent; `clojure -M:dev-local:features` also 0 failures; CI green on the landing commit. Scenario intent unchanged; no @wip.
