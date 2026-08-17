---
# isaac-rxr4
title: 'Backfill: transcripts -> scenes + gists (segmentation + gisting command)'
status: draft
type: task
created_at: 2026-08-17T03:21:48Z
updated_at: 2026-08-17T03:21:48Z
parent: isaac-51xy
blocked_by:
    - isaac-5lri
---

Phase-1 bean 2 of isaac-51xy. Derive scene records + gists from existing session transcripts via an LLM one-pass segmentation+gisting command. Blocked design forks to settle in planning: segmentation pass shape (prompt, output format, gisting model), scene/gist file schemas + locations (first durable artifacts — shapes stick), tool-noise stripping rules, idempotency/re-run semantics. Depends on isaac-5lri (embedding seam).
