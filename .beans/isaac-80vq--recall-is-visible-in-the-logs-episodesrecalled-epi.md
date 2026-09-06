---
# isaac-80vq
title: 'Recall is visible in the logs: :episodes/recalled, :episodes/recall-empty, :recall/scene, :recall/search'
status: completed
type: feature
priority: normal
tags:
    - episodes
    - recall
    - logging
created_at: 2026-09-04T14:25:43Z
updated_at: 2026-09-06T18:46:23Z
parent: isaac-51xy
---

Micah (2026-09-04): "I would very much like to see recall in the logs." Today recall-at-open only writes :recalled-scenes into the episode record and the recall tools log nothing (only :recall/skipped warns), so an operator reading the log cannot tell whether a turn remembered anything. Field evidence 2026-09-04 14:14–14:16Z: marvin's Discord and ACP episodes both recalled the Fermi scene at open and one turn fetched it with recall__scene — none of it in server.log or cli.log.

## Events (all :info unless noted)
- `:episodes/recalled` — emitted by `isaac.recall.inject/inject-on-open!` after the blocks are appended: `:crew :episode :thread :query-chars :lineage <n> :search <n> :scene-ids [..] :top <best cosine> :floor <floor-cos>`. One event per open, even when only lineage was injected (search 0).
- `:episodes/recall-empty` — same site, when neither lineage nor search injected anything: `:crew :episode :thread :query-chars :best <best cosine or nil> :floor`.
- `:episodes/recall-skipped` (:debug) — warm turn or missing query, with :reason.
- `:recall/scene` — `isaac.recall.tools/scene-tool`: `:crew :scene :episode :chars`; missing scene → :warn `:recall/scene-missing`.
- `:recall/search` — `isaac.recall.tools/search-tool`: `:crew :query-chars :hits <n> :top :floor`.
No prompt/scene text in the events (gists are fine in :debug only).

## Features (`@wip`) — isaac-agent `features/episodes/recall_logging.feature` @ 136e1bb
1. recall-at-open logs what it injected (search 1, lineage 0, top, floor 0.47)
2. a query that clears nothing logs the best score it saw
3. the recall tool logs the scene it fetched
(recall__search: unit spec on the tool, no extra scenario.)

## Step ledger
| step | status |
|------|--------|
| isaac EDN file exists with / config file containing / crew has a closed episode with scenes / isaac is run with / exit code is 0 / model responses queued (tool_call rows) / built-in tools registered / crew allows tools / the log has entries matching | reuse — the whole file reuses live.feature's recall-at-open fixture |

## Acceptance
    cd isaac-agent
    bb features features/episodes/recall_logging.feature features/episodes/live.feature features/episodes/index.feature
    bb spec spec/isaac/recall
    clojure -M:features && bb spec   # full gate, exit codes (unwrapped; the 60s wrapper lies — see the test-support bean)
Remove @wip when green. Note for the ACP surface: its process logs to cli.log, not server.log (separate follow-up if a single stream is wanted).

## Handoff

branch: bean/isaac-80vq @ d6a43c5a5923a35898662c63df3a54b43612cecb (base origin/main@1ee29cd4e93bd0815e53d8defba6ce84695c7f4b)

scrapper@isaac-work-1: recall-at-open logs :episodes/recalled / :episodes/recall-empty / :episodes/recall-skipped (debug). scene-tool logs :recall/scene / :recall/scene-missing. search-tool logs :recall/search. @wip removed. Acceptance green: bb features recall_logging+live+index 27/0/163; bb spec spec/isaac/recall 81/0/188; bb spec 1660/0/3449; clojure -M:features 767/0/2024.

## Landed on main (2026-09-06)

main-sha: isaac-agent d6a43c5a5923a35898662c63df3a54b43612cecb



## Deployed (2026-09-06 18:45Z)
Agent 0.1.48 (8d70109) pinned + upgraded on zanebot, restarted; carries 80vq plus 6zk5, jgng, 4zr3 (all verified-landed). Smoke: `isaac prompt --crew marvin 'do you recall anything about Fermi…'` → Marvin answered from the Fermi Explorer scene; recall events checked in server.log (see planner note).

Smoke result: three cold opens (CLI `prompt --crew marvin`, CLI `--session` new thread, ACP session/new + prompt) each logged `:episodes/recalled :search 8 :lineage 0 :top 0.74–0.76 :floor 0.47 :scene-ids […]` with the thread and episode ids — in **cli.log**, because turns started through the remote CLI (which runs in-process on the server) log under the CLI logger; Discord-originated turns will land in server.log. Operator note recorded.
