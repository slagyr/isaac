---
# isaac-why8
title: Version-tag the module repos; switch inter-module deps from :git/sha to tags
status: in-progress
type: task
priority: normal
created_at: 2026-06-18T14:32:19Z
updated_at: 2026-06-18T22:25:00Z
---

Today only isaac-foundation has a version tag (v0.1.0). The other 8 modules — agent, server, acp, cron, hail,
hooks, discord, imessage — have NONE, and inter-module deps.edn reference each other by :git/sha. That is brittle
(every foundation change forces a SHA bump in every downstream repo) AND blocks a clean module registry
(modules.edn needs stable versioned coords).

## Goal
- Tag v0.x.0 on each of the 9 module repos.
- Switch inter-module deps.edn references from :git/sha to :git/tag.

## Approach
- Versioning scheme: v0.1.0 baseline; bump per release.
- Order by dependency: foundation (has v0.1.0) -> agent -> server/acp -> cron/hail/hooks -> discord/imessage.
  Tag a repo, push the tag, then bump its consumers' deps to that tag.
- Decide how tags get cut going forward: manual `git tag` + push, or a small release workflow per repo.

## Acceptance
- All 9 module repos have a version tag.
- Inter-module deps reference :git/tag (not :git/sha).
- Everything still resolves and bb ci is green across the repos.

## Why now
Prerequisite for the module registry (modules.edn) and the `isaac modules install` UX (isaac-dhzy). Also good
hygiene independent of the registry.

## Verification notes

- Verification failed on 2026-06-18. The bean is now `in-progress` again because acceptance still fails on repo metadata before the CI sweep.
- Missing tags: `v0.1.0` is present in `isaac-foundation`, `isaac-acp`, `isaac-discord`, and `isaac-imessage`, but still absent in `isaac-agent`, `isaac-server`, `isaac-cron`, `isaac-hail`, and `isaac-hooks`.
- Remaining raw inter-module SHA pins still block acceptance. Examples on current heads: [isaac-agent/deps.edn](/Users/micahmartin/agents/verify/isaac-agent/deps.edn:3) still uses bare `:git/sha` for `isaac-foundation`; [isaac-server/deps.edn](/Users/micahmartin/agents/verify/isaac-server/deps.edn:3) still uses bare `:git/sha` for `isaac-foundation`; [isaac-hooks/deps.edn](/Users/micahmartin/agents/verify/isaac-hooks/deps.edn:3) still uses bare `:git/sha` for `isaac-foundation` and `isaac-agent`; [isaac-hail/deps.edn](/Users/micahmartin/agents/verify/isaac-hail/deps.edn:3) still uses bare `:git/sha` for `isaac-foundation`, `isaac-agent`, and `isaac-server`; [isaac-cron/deps.edn](/Users/micahmartin/agents/verify/isaac-cron/deps.edn:6) still uses bare `:git/sha` for `isaac-agent`.
- What is correct so far: `isaac-acp`, `isaac-discord`, and `isaac-imessage` have moved their inter-module deps to `:git/tag` plus companion SHA form.
- I did not run `bb ci` across the repos on this pass because the acceptance already fails its static gate on missing tags and remaining SHA-only inter-module dependencies.
