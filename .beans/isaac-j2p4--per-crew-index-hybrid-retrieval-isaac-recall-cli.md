---
# isaac-j2p4
title: Per-crew index + hybrid retrieval + isaac recall CLI
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-08-17T03:21:48Z
updated_at: 2026-08-18T22:09:22Z
parent: isaac-51xy
---

Phase-1 bean 3 of isaac-51xy. Embed scenes/gists into the per-crew index (rows {scene-id, kind: text|gist, vector, embedding-model}); brute-force cosine blended with recency; lexical channel; 'isaac recall <query>' CLI for the retrieval-quality checkpoint. Feeds the checkpoint that gates all live changes.

## Decisions (2026-08-18, Micah)

1. **Embed BOTH gist and text per scene.** They fail in opposite directions: text vectors carry specifics (identifiers, filenames) but dilute over long scenes and truncate at the embedder's context window; gist vectors are clean topical points but generalized. Checkpoint data decides if one channel gets dropped later — index rows are cheap and rebuildable.
2. **Scoring = weighted sum of channels, weights are multipliers ("parts").** `score = (w-text·cos_text + w-gist·cos_gist + w-lex·lexical + w-recency·recency) / Σw`. Normalizing by the weight sum keeps scores comparable across weight settings — "1 part gist, 2 parts recency" reads literally.
3. **Weight resolution order: hardcoded defaults → `:recall` config → CLI flags** (flags highest, for checkpoint sweeps). Config support lands in THIS bean, not deferred.
4. **Recency is an additive channel, not multiplicative decay.** `recency = 0.5^(age/half-life)`, half-life default 30 days. Additive biases toward fresh scenes but lets an ancient perfect match still win — the point of episodic memory. Micah notes (2026-08-18) this could later interact with the weights model; additive chosen as the simple start. Hard fading (multiplicative floor) can layer on later if the checkpoint demands it.
5. **Index location**: `<root>/episodes/<crew>/index.ednl` — derived data, rows `{:episode-id :scene-id :kind :model :vector}`, always rebuildable from scene .md files.
6. **Backfill**: `isaac episodes index [--crew C]`, idempotent by (scene-id, kind, model); `--rebuild` re-embeds everything. Rows tagged with embedding model. **Model switch keeps old rows (confirmed 2026-08-18 over auto-prune)**: a stale index is a *forcing step* — recall hard-errors when ZERO rows match the configured model ("run isaac episodes index"), and warns when mismatched rows sit alongside matching ones. Rationale (Micah): loudly broken beats quietly diminished — auto-prune would leave the system quasi-working with half its brain missing and no signal. `--rebuild` is the cleanup.
7. **Query CLI**: `isaac recall <query> --crew C -n N` with per-channel score breakdown in the output — the checkpoint needs to see WHY a scene ranked.
8. **Lexical channel is new, purpose-built** term-overlap scoring over gist+text. (The "existing keyword search" — memory_search — is regex line-grep over crew memory files, not a scoring channel; nothing to reuse.)
9. **Tie-break is scene-id ascending** (chronological — timestamped ids). Makes `--w-recency 0` observable: identical-content scenes flip from newest-first to oldest-first.
10. **Server obligation → bean 4 (2026-08-18, Micah)**: when live recall rides the server, embedding-config drift under hot-reload must produce LOUD log entries and user-facing errors — the CLI stderr forcing-step behavior here is the model, but a running server must not degrade silently.

## Scenarios (2026-08-18, committed @wip)

- `features/episodes/index.feature` — 5 scenarios: help; gist+text rows with exact grover vectors; idempotent re-run + `--rebuild` (content mutation proves keyed-not-hashed); no-embedding degradation; model switch keeps old rows.
- `features/recall/query.feature` — 6 scenarios: help (full flag surface); ranked hits with per-channel breakdown (output contract: header + `<rank>. <scene-id> score/text/gist/lex/rec` + indented gist); lexical channel with embeddings zeroed (identifier in text, not gist); weight precedence defaults→config→flags (winner flips twice); recency + `--w-recency 0` + `--half-life` (exact 0.25/0.5 decay values); missing index / model drift / stale-row warning lifecycle.

## Step ledger

| step | status |
|---|---|
| Given an Isaac root at {string} | reuse |
| Given the isaac EDN file {string} exists with: (table) | reuse |
| Given config file {string} containing: (docstring) | reuse |
| When isaac is run with {string} | reuse |
| Then the stdout matches: / stdout contains / stderr contains / exit code | reuse |
| **Given crew {string} has a closed episode {string} with scenes: (table)** | **NEW — writes episode.edn + scene .md via store/write-episode!; synthesizes start/end-ids, :seal-reason :migrate** |
| **Then the index for crew {string} has rows: (table)** | **NEW — reads episodes/<crew>/index.ednl; matches the EXACT row set (count included), so rebuild scenarios prove deletion** |
| **Then no index exists for crew {string}** | **NEW — asserts index.ednl absent (no half-written file)** |
| **Given the current time is {string}** | **NEW — pins "now" for recency math; fixtures must not rot with the calendar** |

## Acceptance

Remove `@wip` tags and both files pass:

```
bb features features/episodes/index.feature
bb features features/recall/query.feature
```

Existing suites stay green: `bb features features/episodes/migrate_session.feature`, `bb features features/recall/embedding.feature`, `bb spec spec/isaac/episodes spec/isaac/recall`.

Clean cutover — no legacy index formats, no back-compat aliases; the index file is new surface. Production module: `isaac.recall.*` for scoring/index, `isaac.episodes.cli` gains the `index` subcommand, new top-level `recall` CLI command.


## Implementation (scrapper@isaac-work-1, 2026-08-18)

Agent **1e5a4ff** on main. @wip dropped. Features + specs green.

Product:
- `isaac.recall.score` — cosine, recency 0.5^(age/half-life), lexical term-overlap, blend / Σw, resolve-weights defaults→:recall config→CLI flags.
- `isaac.recall.index` — `<root>/episodes/<crew>/index.ednl` EDNL rows `{:episode-id :scene-id :kind :model :vector}`. Idempotent by (scene-id, kind, model). `--rebuild` drops then re-embeds. Model switch keeps old rows.
- `isaac.recall.query` — hybrid rank; missing index / zero matching-model rows hard-error; mixed models warn + still rank; ties scene-id ascending; recency calendar age via Period (Jan 10 → Mar 10 = 60 days → rec 0.25).
- `isaac.recall.cli` — `isaac recall [options] <query>` with --crew -n/--top --w-text --w-gist --w-lex --w-recency --half-life. Per-channel breakdown + indented gist.
- `isaac.episodes.cli` — `index` subcommand (--crew, --rebuild).
- Manifest `:isaac/cli :recall` + `:isaac.config/schema :recall`.
- Grover fixture vectors corrected: race=[4 411 114 101], dawn=[4 426 100 110].

Verified:
- `bb features features/episodes/index.feature` 5/0
- `bb features features/recall/query.feature` 6/0
- `bb features features/episodes/migrate_session.feature` 9/0
- `bb features features/recall/embedding.feature` 7/0
- `bb spec spec/isaac/episodes spec/isaac/recall` 87/0
- `bb spec` 1346/0 (3 pending claude-cli @real)
- `bb lint` on touched recall/episodes/cli files 0/0
