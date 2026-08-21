---
# isaac-qxvl
title: 'Live episodes 4a: router, TTL lifecycle, compaction-close, seal-at-close'
status: todo
type: task
created_at: 2026-08-20T23:16:17Z
updated_at: 2026-08-20T23:16:17Z
parent: isaac-51xy
---

Phase 1.5 bean 4a per isaac-51xy decisions 19-20, 26-29. Episodes as managed sessions for :conversation :episodes pilot crews: router at dispatch entry (warm append / cold close+open with :parent-episode lineage), :episodes {:ttl-minutes 60}, compaction closes episode and seeds successor transcript with the summary, seal-at-close via existing segmentation machinery (flagged/partial reuse), :thread on the open episode record, lazy close + explicit episodes close CLI. No recall (4b), no mid-episode scene sealing (bean 5). Sessions untouched for non-pilot crews.


## Scenarios (2026-08-20, committed @wip)

`features/episodes/live.feature` (new, 8 scenarios): open (episode record :open/:thread, backing session = episode id, sessions exact-set proves no thread-named session); warm append (+10min, one episode, one transcript); cold continuation (+105min: close via segmentation, successor with :parent-episode, lineage chain step); seal-at-close (scene table incl. ~ routine flow-through); compaction-close (seeded open episode fixture, banners, successor transcript BEGINS with compaction summary entry, reply completes); explicit `episodes close` (seals now; next prompt chains EVEN INSIDE the warm window — router appends only to :open); non-episode crews keep plain sessions (regression guard); `episodes list` chain view. Plus `help episodes` revision (@wip) gaining close/list rows.

## Step ledger

| step | status |
|---|---|
| default Grover setup / EDN file / config file / model queue / prompt runs / fixed clock / sessions match / crew has N episodes / episode exists matching / scenes matching | reuse |
| **Then that episode's backing session has transcript matching:** | **NEW — resolves the remembered episode's backing session (name = unknowable episode id), delegates to the transcript matcher** |
| **Given the episodes for crew {s} on thread {s} chain by lineage** | **NEW — orders the thread's episodes by id; each successor's :parent-episode equals its predecessor's :id (multi-hop capable)** |
| **Given crew {s} has an open episode on thread {s} with: (kv table)** | **NEW — writes an :open episode record + backing session with tuning (e.g. compaction.head)** |
| **Given that episode's backing session has transcript:** | **NEW — Given twin of the matcher: seeds the remembered episode's backing transcript** |
| an episode exists for crew {s} matching: | reuse, REVISED SELECTION — picks the first episode satisfying the table (was: migrated-from or newest-by-id, which grabs the open successor when targeting the closed predecessor) |

## Production notes

- Router at dispatch entry (bridge/core ensure-session! seam): for :conversation :episodes crews the inbound --session/channel key is the THREAD; resolve to open episode (scan crew's open episodes by :thread), warm (now - last message < :episodes {:ttl-minutes 60}) -> route to backing session; cold/absent/closed -> close-then-open with :parent-episode. Warm/cold test and message timestamps MUST honor memory/*now* (fixed-clock scenarios depend on it).
- Close = segment backing-session transcript via the existing migration pipeline (spans/seal/routine/flagged-partial reuse; episodes are compaction-free by construction so spans are clean), write scenes + episode.edn :closed. Episode records live in episodes/<crew>/<id>/ alongside migration output; open episodes carry :thread, :status :open, crew/cwd/model pins.
- Compaction on an episode crew: instead of in-place splice, close the episode and open a successor whose transcript starts with the compaction summary entry; the triggering turn completes in the successor.
- New CLI: `episodes close --crew C` ("closed N episodes"), `episodes list --crew C` (id/status/thread/scene-count rows).
- NO recall (4b), NO mid-episode sealing (bean 5), NO index writes at close (CLI `episodes index` still the indexer; 4b revisits).
- Sessions for non-episode crews completely untouched.

## Acceptance

Remove @wip; these pass and all previously-green suites stay green:

```
bb features features/episodes/live.feature
bb features features/episodes/migrate_session.feature features/episodes/index.feature features/recall/query.feature
bb spec spec/isaac/episodes spec/isaac/recall
bb features features/bridge/cli-prompt.feature features/session
```

Field check on zanebot after deploy (recorded here): opt one test crew into :conversation :episodes, run a two-prompt warm + cold cycle via CLI, verify episodes list shows the chain and non-pilot crews are unaffected.
