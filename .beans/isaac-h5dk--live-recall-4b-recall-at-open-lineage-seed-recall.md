---
# isaac-h5dk
title: 'Live recall 4b: recall-at-open, lineage seed, recall tools, index-at-close'
status: todo
type: task
created_at: 2026-08-22T21:33:23Z
updated_at: 2026-08-22T21:33:23Z
parent: isaac-51xy
---

The payoff bean: episode crews remember. Per isaac-51xy decisions 3, 20-24 + this session (2026-08-22, Micah).

## Decisions (2026-08-22, Micah + planning session)

1. **Recall-at-open**: when the router opens an episode (cold open or cold continuation), the opening user message is the query. Hits pass the floor (cos >= :floor-cos OR lex >= 0.5) or NOTHING is injected. Tiered injection: top hit gist+distilled text, next 2 gist-only — config :recall {:inject {:full 1 :gists 2}} (planner defaults, pilot-tunable).
2. **Injection is a `recall` transcript EVENT** (decision 21): {query, scene refs, frozen rendered block}, appended at tail before the triggering user message; renders as a user-role message in transcript position (same mechanism as compaction summaries in prompt/builder). Episode record accumulates :recalled-scenes references (origin episode-id + scene-id + recalled-at + query) — references, never copies (decision 22).
3. **Lineage seed**: a cold continuation additionally injects ALL parent-episode scene gists (gist-only, cap 10) as a "previously on" block — direct lineage, no search. Search-recall DEDUPES against lineage-seeded and already-recalled scenes.
4. **Tools, namespaced per isaac-ek0r** (dependency — register under the landed convention): :recall/search (ranked gists + scene ids, same channels/floor as CLI) and :recall/scene (fetch one scene's distilled text by id). Tool results append the same recall event type — frozen into prefix. Pilot crews grant :recall/*. Mid-episode AUTOMATIC re-recall is OUT (bean 5 territory); the tool covers topic shifts.
5. **Index-at-close (Micah confirmed)**: every close (TTL / compaction / explicit) embeds+indexes all scenes sealed by that close, one batch, via the existing idempotent index machinery. Embed unavailable -> scenes seal UNINDEXED with loud log; next `episodes index` catches up. Recall-at-open with embedding down -> skip recall, loud log, turn proceeds (recall never blocks a turn). Index-model drift at open -> loud log + skip (isaac-j2p4 decision 10 obligation).
6. **Scope**: episode crews only (chronicle crews unchanged); no recall UI/comm surface changes; recall CLI untouched.
