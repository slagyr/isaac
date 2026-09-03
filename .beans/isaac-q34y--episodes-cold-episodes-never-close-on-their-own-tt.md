---
# isaac-q34y
title: 'Episodes: cold episodes never close on their own — TTL is only checked on the next message'
status: draft
type: bug
tags:
    - episodes
created_at: 2026-09-03T17:16:10Z
updated_at: 2026-09-03T17:16:10Z
parent: isaac-51xy
---

Observed 2026-09-03 on zanebot (episodes train 0.1.41). Marvin's Discord episode 2026-09-03-1647-xsdf (the Fermi conversation) stayed open, unsealed, and unindexed after the chat went quiet; a question about it over ACP minutes later found nothing because recall only searches sealed, indexed scenes.

Root cause: the episode TTL (`:episodes :ttl-minutes`, default 60) is a predicate, not a trigger. Callers of close are exactly: (1) `resolve-thread!` → `chain-successor!` when the NEXT message on the same thread arrives cold, (2) compaction-close when that episode's session compacts, (3) explicit `isaac episodes close` / `close-open-episodes!`. Nothing runs on a timer (the only interval tasks are the delivery worker tick and the turn-queue tick). A thread that goes quiet stays open indefinitely and its content is invisible to every other thread until someone talks on it again — and then the close happens before the reply, so the first message of the new conversation pays the distill+index cost inline.

Expected: an episode whose last message is older than ttl-minutes closes on its own (seal → distill → index) shortly after it goes cold, so cross-thread recall sees it and the next message on the thread opens a chained successor cheaply.

Design sketch (for planning session): a scheduler interval task (`:episodes/sweep`, e.g. every 60s, same shape as `:turn.queue/tick`) that lists open episodes per episodes-crew and calls close for those past TTL; must skip episodes whose session is in-flight; log `:episodes/closed :reason :ttl-sweep`. Keep chain-successor as the fallback for the race. Related: cheaper live-seal trigger (thread idle N minutes) and/or a lexical pass over open episodes' unsealed tails in recall are separate options — decide in planning.

Draft until @wip scenarios are written: (1) cold open episode closes on the sweep tick, (2) warm episode is left alone, (3) in-flight episode is skipped, (4) after the sweep, recall from another thread surfaces the sealed scene.
