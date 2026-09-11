---
# isaac-lhnq
title: migrate-layout stamps a post-mmod thread session as chronicle while nesting its episodes under it — session.edn and index disagree
status: completed
type: bug
priority: high
created_at: 2026-09-11T04:07:24Z
updated_at: 2026-09-11T04:10:45Z
parent: isaac-b6w0
---

Repo: isaac-agent (src/isaac/episodes/layout.clj). Found rehearsing the zanebot migration (dry run on a copy, 2026-09-11): a flat session dir that is a post-mmod thread (e.g. sessions/discord-1471611820048519304, crew marvin) is planned as a chronicle move and stamped :session-policy :chronicle by move-chronicle!; the episodes referencing it are then nested under sessions/marvin/discord-.../episodes/<eid>/ and ensure-session-edn! skips the existing session.edn but upserts the index row as :episodes. Result: session.edn says chronicle, sessions/index.edn says episodes, and the sessions CLI shows the wrong policy for every live marvin thread. The layout feature only planted the pre-mmod shape (backing session named by the episode id), so this case was never covered.

## Required
- plan: a leftover flat session whose id is the :session-id/:thread of any folded episode is an episodes-owned session — stamp :session-policy :episodes (plan carries :policy per item; move-chronicle! stamps from the item, not a literal).
- Scenario (@wip → green) in features/episodes/layout.feature: migrate-layout with a flat thread session that has its own directory plus episodes/<crew>/<eid> referencing it — after migration session.edn and the index both say :episodes, the episodes are nested under it, the transcript is intact, and a plain flat session stays :chronicle.

## Acceptance
bb features features/episodes/layout.feature green with @wip removed; bb spec && bb features green; dry-run on the zanebot rehearsal copy shows the marvin thread sessions as (episodes).

## Summary of Changes (planner, 2026-09-11)

Landed on isaac-agent main (squash b0f2114; release 0.1.64 = a7476034800c67aea72b4ff719e89cbfe82bd1ec). plan computes thread-sids from leftover episodes; chronicle items for those sids carry :policy :episodes; move-chronicle! stamps from the item; dry-run prints the policy. New layout.feature scenario (post-mmod thread with a flat session dir + episodes/<crew>/<eid>) green; layout.feature 9/0; full features 822/0; spec 1784/1 (the documented intermittent file_spec row, isaac-x4mr).
