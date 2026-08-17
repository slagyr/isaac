---
# isaac-j2p4
title: Per-crew index + hybrid retrieval + isaac recall CLI
status: draft
type: task
created_at: 2026-08-17T03:21:48Z
updated_at: 2026-08-17T03:21:48Z
parent: isaac-51xy
---

Phase-1 bean 3 of isaac-51xy. Embed scenes/gists into the per-crew index (rows {scene-id, kind: text|gist, vector, embedding-model}); brute-force cosine blended with recency; hybrid with existing keyword search (lexical channel); 'isaac recall <query>' CLI for the retrieval-quality checkpoint. Feeds the checkpoint that gates all live changes.
