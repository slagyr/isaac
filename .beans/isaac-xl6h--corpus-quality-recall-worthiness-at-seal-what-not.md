---
# isaac-xl6h
title: 'Corpus quality: recall-worthiness at seal, what-not-how gists'
status: todo
type: task
created_at: 2026-08-20T17:53:50Z
updated_at: 2026-08-20T17:53:50Z
parent: isaac-51xy
---

Checkpoint items 1-3 from the 2026-08-20 variety-panel findings (isaac-51xy field notes). Routine scenes keep lex searchability but contribute no embedding rows; gists describe the what, not the tool mechanics; marker-only scenes auto-routine. Draft — planning session in progress.


## Decisions (2026-08-20, Micah)

1. **Routine scenes lose vectors, not searchability (Micah's challenge, resolved).** The segmentation model marks routine scenes; routine scenes get NO index rows (they exit the semantic clump that elevates every query's noise floor) but remain fully lex-searchable — the lexical channel scans scene files, not the index. A bug identifier inside a routine test-run scene is still findable by exact term; only semantic similarity is off for it.
2. **Marking syntax: a leading `~` on the gist** in the segmentation line format — `<first>-<last>: ~ <gist>` means routine. Parser strips it and sets scene frontmatter `routine: true`. Kept to one character for LLM reliability; non-matching lines still ignored; tiling rules unchanged.
3. **Prompt addition (routine definition):** routine = procedural mechanics with no recallable substance — running tests/suites, loading skills, processing telemetry/webhook streams, reading files to orient. Substantive = decisions, diagnoses, fixes, designs, conversations, findings.
4. **What-not-how gist instruction:** gists describe what was accomplished, discussed, or discovered — tool activity is evidence, not subject. "Testing recall weight precedence" not "Running CLI spec tests".
5. **Empty scenes auto-routine, mechanically.** A scene whose distilled text is blank after removing drop-markers is marked routine WITHOUT LLM judgment (sealing-path check). Tiling stays intact; the scene simply never earns index rows and has nothing for lex to match.
6. **Zero-signal exclusion at query time:** scenes with no index rows AND lex 0 never enter the candidate list. Kills recency-only ranking of empty scenes (the pinky junk-probe exhibit) independent of the floor redesign (which stays a separate checkpoint item).
7. **Index counts:** `episodes index` reports skipped routine scenes ("N new rows, M routine scenes skipped") — visibility without a warning tone; routine is healthy, not an error.
8. **Rollout:** re-migrate the panel corpus with `--force` after landing (gists + tags regenerate), then re-index. Acceptance includes a measured before/after of the scrapper boilerplate fraction actually excluded.
