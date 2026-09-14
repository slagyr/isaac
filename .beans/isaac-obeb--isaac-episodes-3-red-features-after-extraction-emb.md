---
# isaac-obeb
title: 'isaac-episodes: 3 red features after extraction — embedding config checks do not fire through the harness, recall tool scene log missing'
status: todo
type: bug
priority: high
created_at: 2026-09-14T01:59:51Z
updated_at: 2026-09-14T01:59:51Z
parent: isaac-51xy
---

Repo: isaac-episodes (main c12e359, agent pinned at main 104b3c4). `bb spec` 206/0. `bb features` 79 examples, 3 failures:
1. Embedding Seam — config validation rejects an embedding config with an unknown provider: stderr has no rejection.
2. Embedding Seam — config validation rejects an unknown embedding source: same.
3. Recall is visible in the logs — the recall tool logs the scene it fetched: expected log row absent.
The two config scenarios were green inside isaac-agent before 209q; the module contributes the embedding check through :isaac.config/check, so either the contribution is not registered in the feature harness's module index (same family as isaac-kwhb, module side) or the check moved but the manifest entry did not. The recall log row was emitted by the agent's recall tool before the move; find where it went.
Acceptance: bb features 79/0 and bb spec green in isaac-episodes; the module's CI (add one — the repo has no workflow) green; isaac-acp episodes.feature green against this module sha. Do not recut scenario intent.
