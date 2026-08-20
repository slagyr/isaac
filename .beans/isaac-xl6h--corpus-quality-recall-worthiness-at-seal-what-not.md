---
# isaac-xl6h
title: 'Corpus quality: recall-worthiness at seal, what-not-how gists'
status: todo
type: task
created_at: 2026-08-20T17:53:50Z
updated_at: 2026-08-20T17:53:50Z
parent: isaac-51xy
---

Checkpoint items 1-3 from the 2026-08-20 variety-panel findings (isaac-51xy field notes). Routine scenes keep lex searchability but contribute no embedding rows; gists describe the what, not the tool mechanics; marker-only scenes auto-routine. Scenarios committed @wip 2026-08-20; see sections below.


## Decisions (2026-08-20, Micah)

1. **Routine scenes lose vectors, not searchability (Micah's challenge, resolved).** The segmentation model marks routine scenes; routine scenes get NO index rows (they exit the semantic clump that elevates every query's noise floor) but remain fully lex-searchable — the lexical channel scans scene files, not the index. A bug identifier inside a routine test-run scene is still findable by exact term; only semantic similarity is off for it.
2. **Marking syntax: a leading `~` on the gist** in the segmentation line format — `<first>-<last>: ~ <gist>` means routine. Parser strips it and sets scene frontmatter `routine: true`. Kept to one character for LLM reliability; non-matching lines still ignored; tiling rules unchanged.
3. **Prompt addition (routine definition):** routine = procedural mechanics with no recallable substance — running tests/suites, loading skills, processing telemetry/webhook streams, reading files to orient. Substantive = decisions, diagnoses, fixes, designs, conversations, findings.
4. **What-not-how gist instruction:** gists describe what was accomplished, discussed, or discovered — tool activity is evidence, not subject. "Testing recall weight precedence" not "Running CLI spec tests".
5. **Empty scenes auto-routine, mechanically.** A scene whose distilled text is blank after removing drop-markers is marked routine WITHOUT LLM judgment (sealing-path check). Tiling stays intact; the scene simply never earns index rows and has nothing for lex to match.
6. **Zero-signal exclusion at query time:** scenes with no index rows AND lex 0 never enter the candidate list. Kills recency-only ranking of empty scenes (the pinky junk-probe exhibit) independent of the floor redesign (which stays a separate checkpoint item).
7. **Index counts:** `episodes index` reports skipped routine scenes ("N new rows, M routine scenes skipped") — visibility without a warning tone; routine is healthy, not an error.
8. **Rollout:** re-migrate the panel corpus with `--force` after landing (gists + tags regenerate), then re-index. Acceptance includes a measured before/after of the scrapper boilerplate fraction actually excluded.

9. **`no rows for model` re-keys to model MISMATCH, not emptiness (scenario-forced).** An all-routine corpus legitimately has an empty index and lex still serves it; drift (config model != index :model) stays a hard error. A no-overlap query on a zero-signal corpus prints "no hits", exit 0.

## Scenarios (2026-08-20, committed @wip)

- `features/episodes/migrate_session.feature` — 3: tilde-marked scenes seal routine (~ stripped from stored gist; frontmatter routine: true only when true); marker-only scenes auto-routine (mechanical, no LLM judgment; tiling intact); segmentation prompt carries routine + ~ + "evidence, not the subject" + "what was accomplished" (lookahead pattern, order-free).
- `features/episodes/index.feature` — 1: routine scenes earn no rows ("2 new rows, 1 routine scene skipped"; exact row set proves exclusion).
- `features/recall/query.feature` — 2: routine scene surfaces at rank 1 via lex on an exact identifier (embedding channels 0.0) and never on topical queries (zero-signal exclusion, stdout does not contain it); rowless index serves lex, junk query on routine-only corpus prints "no hits" exit 0.

## Step ledger

| step | status |
|---|---|
| all Given/When/Then steps | **reuse** — no new steps |
| crew {string} has a closed episode {string} with scenes: | reuse, REVISED IMPLEMENTATION (pass `routine` column through to scene frontmatter) |
| that episode has scenes matching: | reuse (routine column rides the generic matcher; empty cell asserts key absence) |

## Production notes

- store.clj SCENE_FRONTMATTER_KEYS gains :routine (written only when true).
- segment.clj: parser strips leading `~` from gists, sets :routine? ; sealing path auto-marks scenes whose distilled text is markers-only.
- distill.clj: SEGMENT_INSTRUCTIONS gains routine definition (decision 3) + what-not-how (decision 4) with the exact phrases the prompt scenario pins.
- index.clj: skip routine scenes for rows; report "N routine scenes skipped".
- query.clj: candidates = scenes with index rows OR lex > 0; empty candidates -> "no hits" exit 0; :no-rows error only on model mismatch.

## Acceptance

Remove @wip; these pass and all previously-green suites stay green:

```
bb features features/episodes/migrate_session.feature
bb features features/episodes/index.feature
bb features features/recall/query.feature
bb spec spec/isaac/episodes spec/isaac/recall
```

Rollout (operator step after completion, recorded here): re-migrate the panel corpus on zanebot with --force (gists + routine tags regenerate via grok-4.20), re-index all crews, and record the measured routine fraction per crew (expect scrapper >50%, pinky ~100%) plus a before/after junk-query comparison.
