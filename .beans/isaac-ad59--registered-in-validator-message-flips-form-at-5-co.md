---
# isaac-ad59
title: registered-in? validator message flips form at 5 contributions; scenarios that pin the wording break when a berth's count crosses it
status: draft
type: bug
tags:
    - foundation
    - schema
created_at: 2026-09-04T20:07:23Z
updated_at: 2026-09-04T20:07:23Z
---

`isaac.schema.registered-in` (registered_in.clj:114-117): with ≤5 accepted contributions the failure reads `must be one of [...]`; with more, `must be a registered contribution to <berth>`. isaac-agent's config/cli.feature:179 and module/api_extension.feature:56/:70 pin the second form; extracting claude-cli (isaac-jllj) drops core llm-api from 6 to 5 and all three flip red with no product change. Fix: one stable message that carries both — `must be a registered contribution to <berth> (one of: a, b, c …)` (elide past the cap with `… +N`), keep `:message` in sync, and update the three agent scenarios to the stable form when foundation ships. Ships via the foundation tag + brew train.
