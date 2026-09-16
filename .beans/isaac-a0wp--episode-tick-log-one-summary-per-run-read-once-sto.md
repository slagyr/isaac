---
# isaac-a0wp
title: 'Episode tick: log one summary per run, read once, stop re-warning every 30s'
status: in-progress
type: task
priority: normal
created_at: 2026-09-16T15:15:42Z
updated_at: 2026-09-16T15:45:46Z
---

## Problem

The episodes worker ticks every 30s (`worker.clj`, `(def default-tick-ms 30000)`, scheduled as `:episodes/tick` on `{:kind :interval :ms tick-ms}`; zanebot does not override it — its whole episodes config is `:episodes {:gist-model :grunt}`). Each tick walks every crew's open episodes and calls `maybe-seal!` + `maybe-close-if-cold!`.

Three log/IO problems, measured on zanebot 2026-09-16:

1. **Two transcript reads per episode per tick.** `lifecycle.clj:112` and `:115` both call `impl-common/read-transcript-raw` — once for the backing session, once for the episode — and each logs `:session/transcript-read` at debug. An idle session shows exactly 4 reads/minute, forever: `marvin/20260914035613548` read at 14:43:06 / 14:43:08, 14:43:37 / 14:43:39, 14:44:05 / 14:44:06 … all 120 entries, nothing changed. That is 4 log lines per minute per open episode across every crew.
2. **No tick summary.** There is no log line saying the worker ran, how long it took, or what it did. The only episode-level events are outcomes (`episodes/live-sealed`, `episodes/closed`, `episodes/seal-failed`).
3. **Failures repeat every tick with no backoff.** During the chatgpt 429 wall, `episodes/seal-failed :reason :provider-error` fired every 30s for the same episode from 14:10:06 through 14:14:38 — ten identical warns — then succeeded at 14:15:48 once quota returned. A provider outage makes the sweep spam warn, which would pierce even an info-level watch.

## Decisions (2026-09-16, Micah)

- Wants a log entry every time the episode worker runs, with how long it took.
- Wants a scene being cut (sealed) logged as well — `episodes/live-sealed` and `episodes/closed` already do this at info; keep them.
- Logging discipline generally: one line per unit of work at info, the inside of that work at debug. A transcript read is not a unit of work; it is a step inside one.

## Proposal

1. **`:episodes/tick` at info, one per run**, with `:elapsed-ms`, `:crews`, `:episodes-examined`, `:sealed`, `:closed`, `:skipped-in-flight`. A 30s heartbeat that is readable at info level.
2. **One transcript read per episode per tick.** `lifecycle.clj:112`/`:115` read the same data twice; read once and pass it down. Better still, skip the read entirely when the transcript's mtime has not moved since the last tick — an idle episode should cost a stat, not two full reads.
3. **`seal-failed` backs off or reports a streak.** Log the first failure per episode at warn, then either back off the retry interval or log subsequent failures with a `:consecutive` count rather than one line per tick.
4. Per-read `:session/transcript-read` lines stay at debug (no `:trace` level — see [[isaac-1hs0]]); they become readable once `isaac logs --level` lands.

## Acceptance

- `bb spec` and `bb features` green in isaac-episodes (`bb ci` runs both).
- A tick over N open episodes logs exactly one `:episodes/tick` info entry carrying elapsed-ms and the counts; an idle episode contributes `:skipped-in-flight` or a no-op count rather than seal/close events.
- An idle episode whose transcript has not changed produces at most one transcript read per tick (assert read count, not timing).
- Repeated seal failures for one episode produce one warn plus a streak count or backed-off retries — not one warn per tick.
- Verified on zanebot after deploy: `:episodes/tick` appears every ~30s, and `session/transcript-read` lines for idle sessions drop from 4/min to at most 2/min (one per tick) or to zero when unchanged.

## Note

isaac-episodes is its own repo (`isaac.session.episodes`, deployed sha `e0c7ddd`; gitlib checkout on zanebot is `0cbe24b`). Tests: `bb spec`, `bb features`, `bb ci`. Features live under `features/episodes` and `features/recall`.

## Scenarios (committed @wip, 2026-09-16)

isaac-episodes `features/episodes/idle_seal.feature` — 3 scenarios, all reusing existing steps (`the episodes worker ticks at {iso}`, `the log has entries matching:`, the standard prompt/queue setup). No new steps.

1. **the worker logs one summary per tick** — one `:info :episodes/tick` entry carrying `episodes-examined`, `sealed`, `closed`, `elapsed-ms`.
2. **an unchanged episode is not re-read on the next tick** — the second tick's summary reports `transcript-reads 0`.
3. **repeated seal failures report a streak instead of one warn per tick** — two failing ticks produce a `:episodes/seal-failed` entry with `:consecutive 2`.

Design note from scenario 2: the tick summary must carry a **`:transcript-reads`** count alongside the other counters. That makes "we stopped re-reading" observable from the same line you wanted for the heartbeat, and avoided inventing a log-clearing step to isolate the second tick.
