---
# isaac-ozh5
title: 'isaac-episodes: recall ledger — an opt-in per-crew record of every recall (query, candidates, scores, what was injected) for research'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-25T16:32:30Z
updated_at: 2026-09-25T16:39:55Z
---

## Why (Micah, 2026-09-25)

We cannot tell whether episode recall finds the right prior topic: the
server log records only `:query-chars`, `:top` and scene ids. One sampled
recall on yopp ("what tools did you use to answer that question?") surfaced
three cross-session scenes about jokes and tools at ~0.60 — plausible vibe,
not the topic. Micah: keep a research log of recalls **inside the crew**, not
the regular logs, and make it a configuration that can be turned on and off.

## Design

- Config `recall.ledger` (boolean, default **false**) under the existing
  `:recall` map. Off: nothing is written, behaviour unchanged.
- On: every recall for a crew — the automatic turn-start injection
  (`isaac.recall.inject`) and the `recall__search` tool — appends one EDN
  line to `sessions/<crew>/recall/ledger.ednl` (beside `index.edn` and
  `vectors.json`, which `episodes.layout` already reserves):
  `{:ts :kind (:inject | :search) :session :thread :lineage :query :floor
    :top :candidates [{:scene-id :episode :score :gist}] :injected [scene-id …]}`
  — `:candidates` is every scene that was scored above the floor, in score
  order, capped at 20; `:injected` is what actually reached the prompt.
- The query is the full text used for embedding. Ledger writes never fail a
  turn: an IO error logs `:recall.ledger/write-failed` once at warn.
- Reading is `cat`/`grep` on the file; no CLI in this bean.

## Acceptance (features/recall/ledger.feature — baselined)

- [ ] Scenario "with recall.ledger on, a turn-start recall is appended to the
  crew's ledger (isaac-ozh5)": new step `the crew "<crew>" recall ledger has
  entries matching:` (table over the EDN lines: kind, session, query, a
  regex on candidates, injected).
- [ ] Scenario "with recall.ledger on, a recall__search call is appended
  with kind :search (isaac-ozh5)".
- [ ] Spec: default off writes nothing; a write failure warns once and the
  recall still returns; candidates capped at 20.
- [ ] Manifest schema for `recall.ledger`; version bump; bb spec / bb
  features / bb lint green.

Likely repo scope: isaac-episodes (recall/inject.clj, recall/tools.clj,
recall/index.clj or a new recall/ledger.clj, manifest, features/recall).

feature-baseline: isaac-episodes 51c9ba6a1bf7d90719036da9df76c165efcf9e7c
feature-blob: isaac-episodes features/recall/ledger.feature 2cdc1380c3fb010863e19956a3add1a18acfd903 27,38
