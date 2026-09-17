---
# isaac-y3q6
title: Recall floor-cos lives on the embedding model; select-injected is the live path
status: draft
type: task
created_at: 2026-09-17T00:07:10Z
updated_at: 2026-09-17T00:07:10Z
parent: isaac-51xy
---

## Problem

Scoring knobs that classify embedding cosine (especially `:floor-cos` 0.47) were treated as generic `:recall` config. They are properties of the embedding model. nomic-embed-text's lived-in floor is 0.47; `text-embedding-3-large` needs its own number. Drift is a second cosine threshold on a different pair type (open-tail vs last exchange) and is **off** in production — out of scope.

The rank-then-admit pipeline (blend → shortlist 8 → cos-floor OR lex-floor → 1 full + 2 gists; thread gists are a separate block) is scattered across `score.clj` / `query.clj` / `inject.clj`. Markdown would rot. The documentation is a live Clojure function.

## Decisions (2026-09-16, Micah)

- Two cosine thresholds, not one atomic unit. `floor-cos` (query ↔ sealed scene, admit when ≥) and `drift-threshold` (rolling open-tail ↔ last exchange, seal when <). Cannot derive one from the other. Drift stays off; do not add it to operator embedding config in this bean.
- Only `floor-cos` must travel with `:model`. Lex-floor (0.5), shortlist 8, inject 1+2, thread-gists 10, weights, recency-half-life, message-cap, idle-minutes stay code/existing `:recall`/`:seal` defaults — **not** newly exposed, **not** renamed in this bean.
- Clean cutover: drop `:recall :floor-cos`. Resolve `defaults (0.47) → [:episodes :embedding :floor-cos] → CLI --floor-cos`. Leftover `:recall :floor-cos` does not apply (schema drops unspecified keys; code must not read that path).
- Document the selection process as a live function `select-injected` that `inject-on-open!` calls. Named constants at the top of that namespace. Specs hit the function. Not a markdown essay, not a DSL.
- Rank then admit: weights rank; floors admit (OR). All 8 failing both floors → no search block (`:episodes/recall-empty`). Thread block is independent.
- No 3-large floor in this bean (not measured). Default stays nomic 0.47.

## Likely repo

`isaac-episodes`

## Out of scope

- Drift / min-tail on `:embedding`
- Making lex-floor, shortlist, inject, lineage configurable
- Renames (`recency-half-life`, `message-cap`, inject map keys)
- Markdown `doc/recall.md`
- Re-index / zanebot floor calibration
