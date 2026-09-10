---
# isaac-9tjo
title: TTL sweep logs :episodes/closed unconditionally; an empty successor episode (summary-only transcript) can never be sealed and is retried every 30 s forever
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-09-10T01:04:02Z
updated_at: 2026-09-10T02:58:28Z
---

Repo: isaac-agent (`episodes/lifecycle.clj` `maybe-close-if-cold!` + `close-episode!`; `episodes/worker.clj` tick). Agent 0.1.53. Found while cleaning up after isaac-jom5.

## Evidence (zanebot, marvin, 2026-09-09 21:1xZ → 2026-09-10 01:0xZ)
- Six successor episodes (2026-09-09-2114-junk … 2130-nf7j) opened by the compaction loop; each backing transcript = a session marker + ONE compaction entry (5.3 KB), zero messages.
- server.log: `:episodes/closed :reason :ttl-sweep` for each of the six every ~30 s — 456 lines for 2114-junk alone; `episode.edn` mtime unchanged since creation, `:status :open`. `isaac episodes close --crew marvin` → 'closed 0 episodes' (its count only tallies results with status closed/partial/resumed).
- Code: `maybe-close-if-cold!` calls `close-episode!` and then logs `:episodes/closed … :reason :ttl-sweep` and returns `{:status :closed}` UNCONDITIONALLY. `close-episode!` runs `migrate/migrate-session!`; with nothing to segment it yields no `:episode`, so `closed` is nil and NOTHING is written — the caller cannot tell.

## Required
1. An open episode with nothing to seal (no messages, or only markers/compaction entries) closes on the sweep with zero scenes: write `:status :closed :ended-at now` (preserve :thread/:parent-episode), no LLM pass.
2. `maybe-close-if-cold!` logs `:episodes/closed` only when `close-episode!` actually closed (result status closed/partial/resumed); otherwise `:episodes/close-failed :reason …` at :warn, once, and the sweep backs off that episode (not every tick).
3. `isaac episodes close` reports per-episode failures instead of 'closed 0'.

## Scenario (@wip, planted isaac-agent 77d9e6a, features/episodes/idle_seal.feature)
- an open episode with nothing to seal closes on the TTL sweep with zero scenes, once — existing steps (file fixtures, the episodes worker ticks at, episode exists matching, has 0 scenes, log has entries) + the negative log step (from isaac-o2fh if landed, else NEW).

## Acceptance
- the planted scenario green with @wip removed; `bb features features/episodes/ && bb spec` green
- Train: after deploy the six marvin episodes on thread acp-4cb3db22… close on the next sweep and stay closed; `:episodes/closed` lines for them stop.



## REVISED (2026-09-10, Micah) — supersedes Required 1–2
1. **Delete, don't close.** An open episode with no content (no messages — only markers/compaction entries) has no value: the sweep DELETES the episode record and its backing session (`:episodes/deleted :reason :empty`). No LLM pass, no zero-scene record.
2. **Logging is truthful about time**: log `:episodes/closing` before the attempt; `:episodes/closed` (or `:episodes/deleted`) only after it succeeded; on failure `:episodes/close-failed :error <message>` at :warn with the actual error (e.g. 'nothing to segment', 'unknown backing session'), and the sweep backs off that episode.
3. `isaac episodes close` reports per-episode outcomes (closed / deleted / failed + error), never a bare 'closed 0'.
Planted scenario updated on isaac-agent main d51cba9 (asserts the record and backing session are gone, `:episodes/closing` then `:episodes/deleted :reason :empty`, and no `:closing` on the next tick).

## Implementation (scrapper@isaac-work-2)

TTL sweep now DELETES empty open episodes (no type=message entries) plus the backing session. No LLM pass, no zero-scene close.

- store/delete-episode! + keywordize-status so planted Gherkin status | open is :open
- lifecycle/maybe-close-if-cold! logs :episodes/closing before the attempt; :episodes/deleted :reason :empty after success; :episodes/close-failed :error at :warn on failure
- close-episode! also deletes empty transcripts
- Worker tick slices :log-entries-mark so the second-tick negative log assertion does not see the first tick's :closing
- CLI isaac episodes close prints per-episode deleted / failed / closed lines; never a bare closed 0

branch: bean/isaac-9tjo @ fbbf1f94c4ce2e3699ca4ca92e7b108aa3c400f7 (base origin/main@d51cba952ead4045e7aa255528cc48e06174e7de)

Evidence: bb features features/episodes/idle_seal.feature 6/0; each features/episodes/*.feature green; bb spec 1719/0. Combined bb features features/episodes/ hits the 180s native suite timeout (pre-existing bb.edn budget).
