---
# isaac-kxdg
title: 'acp activation fails: Unable to resolve symbol lexicon/->id (:isaac.server/route) -> no routes'
status: todo
type: bug
priority: high
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-19T22:22:26Z
---

Every boot: :module/activation-failed {:module "isaac.comm.acp" :berth
":isaac.server/route" :error "Unable to resolve symbol: lexicon/->id"}. acp's
route contribution throws because `lexicon/->id` is unresolved (missing require
or renamed symbol). acp never activates, so its HTTP routes never register — the
"no routes registered" symptom.

Fix in isaac-acp: resolve the lexicon/->id reference (add the require / fix the
symbol). Verify acp activates and registers its route(s) at server boot.
