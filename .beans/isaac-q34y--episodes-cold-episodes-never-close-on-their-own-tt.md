---
# isaac-q34y
title: 'Episodes: cold episodes never close on their own — TTL is only checked on the next message'
status: todo
type: bug
priority: normal
tags:
    - episodes
created_at: 2026-09-03T17:16:10Z
updated_at: 2026-09-03T18:11:31Z
parent: isaac-51xy
---

Observed 2026-09-03 on zanebot (episodes train 0.1.41). Marvin's Discord episode 2026-09-03-1647-xsdf (the Fermi conversation) stayed open, unsealed, and unindexed after the chat went quiet; a question about it over ACP minutes later found nothing because recall only searches sealed, indexed scenes.

Root cause: the episode TTL (`:episodes :ttl-minutes`, default 60) is a predicate, not a trigger. Callers of close are exactly: (1) `resolve-thread!` → `chain-successor!` when the NEXT message on the same thread arrives cold, (2) compaction-close when that episode's session compacts, (3) explicit `isaac episodes close` / `close-open-episodes!`. Nothing runs on a timer (the only interval tasks are the delivery worker tick and the turn-queue tick). A thread that goes quiet stays open indefinitely and its content is invisible to every other thread until someone talks on it again — and then the close happens before the reply, so the first message of the new conversation pays the distill+index cost inline.

Expected: an episode whose last message is older than ttl-minutes closes on its own (seal → distill → index) shortly after it goes cold, so cross-thread recall sees it and the next message on the thread opens a chained successor cheaply.

Design sketch (for planning session): a scheduler interval task (`:episodes/sweep`, e.g. every 60s, same shape as `:turn.queue/tick`) that lists open episodes per episodes-crew and calls close for those past TTL; must skip episodes whose session is in-flight; log `:episodes/closed :reason :ttl-sweep`. Keep chain-successor as the fallback for the race. Related: cheaper live-seal trigger (thread idle N minutes) and/or a lexical pass over open episodes' unsealed tails in recall are separate options — decide in planning.

Draft until @wip scenarios are written: (1) cold open episode closes on the sweep tick, (2) warm episode is left alone, (3) in-flight episode is skipped, (4) after the sweep, recall from another thread surfaces the sealed scene.



## Requirement (2026-09-03, Micah)

Closing the episode is not the point. Content must be recallable from a different episode within a few minutes of being said, whether or not the source episode has closed. Anything longer is too long.

## Design direction (planner, for review)

Add an **:idle trigger** to `maybe-seal!` alongside :size-cap and :drift. Fired by a scheduler tick (`:episodes/tick`, every 30s — same shape as `:turn.queue/tick`) for every open episode whose last message is older than `:episodes {:seal {:idle-minutes N}}` (default 3) and whose session is not in flight. Idle seals with `leave-open 0` (seal the whole tail, including the current scene) — the segmentation contract already supports `(cont a-b)` continuation marks, so a conversation that resumes after the idle gap appends a continuation scene rather than a fragment. Seal = distill (one gist-model call) + embed (local nomic) + index, exactly the existing live-seal path; the episode stays open and warm.

Latency bound = idle-minutes + one tick (≈3.5 min worst case with defaults). Idle-minutes is the only knob that matters; tick cadence just bounds slop.

The TTL close sweep rides the same tick as housekeeping (close cold episodes so the next message chains cheaply) but is no longer what makes content recallable.

Not chosen: searching unsealed transcript tails from recall (no gists → poor rank quality, provisional rows to retract). Open question for planning: should an idle seal re-run when the same scene continues (re-gist the merged scene) or leave the (cont) split as-is.



## Decisions (2026-09-03, Micah: good)

1. Trigger: open episode, last message older than `:episodes {:seal {:idle-minutes 3}}`, unsealed tail non-empty, session not in flight.
2. Tick: scheduler task `:episodes/tick`, `{:kind :interval :ms 30000}`, registered like `:turn.queue/tick` (new `isaac.episodes.worker` start!/stop!); walks open episodes of every episodes crew.
3. Idle seal seals the WHOLE tail (`leave-open 0`), seal-reason `:idle`; a resumed conversation appends a continuation scene (segmenter `(cont a-b)` marks).
4. TTL close rides the same tick, after the idle pass: cold past ttl-minutes → close (already sealed, so cheap). `chain-successor!` on next message stays as the fallback.
5. Failure: warn (`:episodes/seal-failed`), next tick retries; no counter, no attention.
6. In-flight skip: unit-spec obligation (not Gherkin) — tick must skip an episode whose backing session is in flight.

## Features (`@wip`) — isaac-agent `features/episodes/idle_seal.feature` @ f5f6345

1. an idle thread seals its tail on the tick and stays open
2. a warm thread is left alone
3. resuming after an idle seal continues the same episode
4. another thread can recall the sealed scene within minutes
5. a cold episode closes on the tick and the next message chains a successor

## Step ledger

| step | status |
|------|--------|
| isaac EDN file exists with / config file containing / current time is / model responses queued / isaac is run with / exit code is 0 | reuse |
| an episode exists for crew … matching / that episode has scenes matching / crew … has N episodes / chain by lineage / the last LLM request matches | reuse |
| **When the episodes worker ticks at "…"** | **NEW — mirrors `the turn queue ticks at` (spec/isaac/turn/queue_steps.clj:127): set memory/now, run one tick** |
| **that episode has N scenes** | **NEW — count variant of `has scenes matching`** |
| **the index for crew "…" has a row for gist "…"** | **NEW — lookup by gist; the exact-row step (`has rows:`) needs ids/vectors a live seal does not fix** |

Fixture notes (adjust fixtures to the contract, never the impl): gist spans are per distilled message (an exchange = 2 entries); scenario 3's continuation gist uses the `(cont a-b)` line form — confirm ordinals against segment.clj's BOUNDARY_LINE; scenario 4 relies on recall-at-open with the grover `mini-embed` stub exactly as live.feature's recall scenario.

## Acceptance

    cd isaac-agent
    bb features features/episodes/idle_seal.feature
    bb features features/episodes/live.feature   # existing seal/close contracts stay green
    bb spec spec/isaac/episodes
Remove @wip when green. Unit spec: in-flight episode skipped by the tick. Ordering: runs on isaac-work when a session is free; independent of the scuttlebutt train.
