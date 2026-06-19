---
# isaac-kxdg
title: 'acp activation fails: Unable to resolve symbol lexicon/->id (:isaac.server/route) -> no routes'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-19T22:35:46Z
---

Every boot: :module/activation-failed {:module "isaac.comm.acp" :berth
":isaac.server/route" :error "Unable to resolve symbol: lexicon/->id"}. acp's
route contribution throws because `lexicon/->id` is unresolved (missing require
or renamed symbol). acp never activates, so its HTTP routes never register — the
"no routes registered" symptom.

Fix in isaac-acp: resolve the lexicon/->id reference (add the require / fix the
symbol). Verify acp activates and registers its route(s) at server boot.

## Worker notes (work-2)

Root cause was a bad manifest `:factory` — `isaac.module/module` does not exist;
`reconcile-modules!` failed before berths ran. Could not reproduce
`lexicon/->id` locally (route berth + handler resolve succeed with pinned and
dev-local classpaths).

Fix: `isaac-acp` f1819a5 — `:factory isaac.module.protocol/module` (matches
discord/hooks/imessage). Added `spec/isaac/manifest_spec.clj` covering factory
resolution, `activate!`, and `/acp` route registration via
`process-manifest-berths!`. `bb spec`: 189 examples, 0 failures.
