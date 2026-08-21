---
# isaac-qxvl
title: 'Live episodes 4a: router, TTL lifecycle, compaction-close, seal-at-close'
status: todo
type: task
priority: normal
created_at: 2026-08-20T23:16:17Z
updated_at: 2026-08-21T00:08:44Z
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

## Held (awaiting human, 2026-08-21)

Escalated to human by **scrapper**@isaac-work-1. Blocking: continuation budget 5/5 exhausted with live episodes 4a still unimplemented (router/TTL/compaction-close/CLI; no product commits past @wip features at 554ab2e).
Resumes only on explicit human action (re-hail the work/plan band, or re-promote). No crew re-picks this until then.

Surveyed (not landed) remaining work:
- Router at dispatch (bridge/core + prompt_cli ensure-session!): `:conversation :episodes` crews treat inbound --session as THREAD; warm append / cold close-then-open with `:parent-episode`. Honor memory/*now*.
- Close = existing migrate/segment/seal; open records `:thread` + `:status :open`. Compaction on episode crew: close then open successor whose transcript BEGINS with compaction summary.
- CLI: `episodes close --crew C`, `episodes list --crew C`. Help episodes close/list. index.feature help is NOT @wip and still expects only migrate-session + index.
- NEW episode_steps: backing-session transcript Then/Given; lineage chain Given; open-episode fixture Given. REVISE episode-exists to first matching table (not newest-by-id).
- Crew schema has no `:conversation`. `:episodes` schema has only `:gist-model`; needs `:ttl-minutes`.
- live.feature uses `prompt ... --session reef-chat --crew cordelia` together; prompt_cli currently rejects that combo. Do not silently break cli-prompt.feature (`prompt --session bridge --crew main`).
- Session store now-iso (memory/sidecar/index) uses Instant/now, not memory/*now*.

Do not re-claim. Do not change grover-vector. No recall 4b, no mid-episode sealing.

## Planner rulings + build order (2026-08-21, re-hail after hold)

Survey accepted — thank you, it found real gaps. Rulings on each blocker:
1. **Schema:** add `:conversation` to the crew schema (enum :episodes; absent = sessions) and `:ttl-minutes` (number, default 60) to `:episodes`. Both are part of THIS bean.
2. **`prompt --session X --crew Y` combo becomes valid everywhere** (planner ruling): `--crew` resolves behavior, `--session` names the container (thread for episode crews). If a session exists with a DIFFERENT crew, keep today's rejection — the change only permits the combo at creation/routing time. cli-prompt.feature must stay green.
3. **Clock:** thread memory/*now* through session-store timestamping (now-iso in memory/sidecar/index stores). Mechanical; do it early — every fixed-clock scenario depends on it.
4. index.feature's help scenario uses presence patterns — new close/list lines cannot break it; no @wip needed there.

Recommended build order (product commits early, no re-survey): schema keys -> clock plumbing -> CLI combo -> episode records + open/close fns (reuse migrate seal) -> router at ensure-session! seam -> compaction-close redirect -> close/list CLI -> steps + de-@wip.
Budget note: if the budget nears exhaustion again, land what is green and hail the plan band with a conflict note rather than holding — partial landings with green suites are welcome.
