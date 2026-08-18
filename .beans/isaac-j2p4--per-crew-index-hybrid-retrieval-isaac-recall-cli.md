---
# isaac-j2p4
title: Per-crew index + hybrid retrieval + isaac recall CLI
status: draft
type: task
created_at: 2026-08-17T03:21:48Z
updated_at: 2026-08-17T03:21:48Z
parent: isaac-51xy
---

Phase-1 bean 3 of isaac-51xy. Embed scenes/gists into the per-crew index (rows {scene-id, kind: text|gist, vector, embedding-model}); brute-force cosine blended with recency; lexical channel; 'isaac recall <query>' CLI for the retrieval-quality checkpoint. Feeds the checkpoint that gates all live changes.

## Decisions (2026-08-18, Micah)

1. **Embed BOTH gist and text per scene.** They fail in opposite directions: text vectors carry specifics (identifiers, filenames) but dilute over long scenes and truncate at the embedder's context window; gist vectors are clean topical points but generalized. Checkpoint data decides if one channel gets dropped later — index rows are cheap and rebuildable.
2. **Scoring = weighted sum of channels, weights are multipliers ("parts").** `score = (w-text·cos_text + w-gist·cos_gist + w-lex·lexical + w-recency·recency) / Σw`. Normalizing by the weight sum keeps scores comparable across weight settings — "1 part gist, 2 parts recency" reads literally.
3. **Weight resolution order: hardcoded defaults → `:recall` config → CLI flags** (flags highest, for checkpoint sweeps). Config support lands in THIS bean, not deferred.
4. **Recency is an additive channel, not multiplicative decay.** `recency = 0.5^(age/half-life)`, half-life default 30 days. Additive biases toward fresh scenes but lets an ancient perfect match still win — the point of episodic memory. Micah notes (2026-08-18) this could later interact with the weights model; additive chosen as the simple start. Hard fading (multiplicative floor) can layer on later if the checkpoint demands it.
5. **Index location**: `<root>/episodes/<crew>/index.ednl` — derived data, rows `{:episode-id :scene-id :kind :model :vector}`, always rebuildable from scene .md files.
6. **Backfill**: `isaac episodes index [--crew C]`, idempotent by (scene-id, kind, model); `--rebuild` re-embeds everything. Rows tagged with embedding model; query-time rows from a different model are skipped with a warning.
7. **Query CLI**: `isaac recall <query> --crew C -n N` with per-channel score breakdown in the output — the checkpoint needs to see WHY a scene ranked.
8. **Lexical channel is new, purpose-built** term-overlap scoring over gist+text. (The "existing keyword search" — memory_search — is regex line-grep over crew memory files, not a scoring channel; nothing to reuse.)
