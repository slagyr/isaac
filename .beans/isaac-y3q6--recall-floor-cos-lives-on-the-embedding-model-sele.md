---
# isaac-y3q6
title: Recall floor-cos lives on the embedding model; select-injected is the live path
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-17T00:07:10Z
updated_at: 2026-09-17T02:16:06Z
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

## Scenarios (committed @wip, 2026-09-16)

isaac-episodes `5ca99b0`.

- `features/recall/query.feature:245` — rewrite: floor resolves defaults, then `[:episodes :embedding :floor-cos]`, then CLI `--floor-cos`; 0 disables
- `features/episodes/live.feature:310` — rewrite: below-floor inject reads embedding `:floor-cos` (not `:recall`)
- `features/recall/query.feature:268` — new: leftover `:recall {:floor-cos 0.999}` does not raise the floor

## Specs (not Gherkin)

`select-injected` is the live selection path `inject-on-open!` calls. Named constants at the top of that namespace (shortlist 8, lex-floor 0.5, search-full 1, search-gists 2, thread-gists 10, default floor-cos 0.47). Specs on synthetic scored scenes:

- rank by blend, then take 8, then admit on `floor-cos` **or** `lex-floor`
- all 8 fail both floors → empty search
- thread gists are a separate block (no floors)
- format: 1 full + 2 gists of admitted search hits

`resolve-floor`: defaults `0.47` → `[:episodes :embedding :floor-cos]` → CLI. Must not read `[:recall :floor-cos]`.

## Implementation

- Add `:floor-cos {:type :double}` under `:episodes :embedding` in the manifest schema; drop it from `:recall`.
- `score/resolve-floor` reads `[:episodes :embedding :floor-cos]`.
- Extract `select-injected`; `inject-on-open!` is I/O around it (embed, log `:episodes/recalled` / `:episodes/recall-empty`, append blocks).
- Clean cutover: no alias for `:recall :floor-cos`.
- Existing fixtures that set `:recall {:floor-cos}` in specs must move to the embedding map (same as the feature rewrites).
- Do not rename other knobs. Do not add drift to embedding config.

## Acceptance

Remove `@wip` from the three scenarios and they pass:

```
bb features features/recall/query.feature:245
bb features features/recall/query.feature:268
bb features features/episodes/live.feature:310
```

Regression (untagged floor/inject scenarios stay green):

```
bb features features/recall/query.feature features/episodes/live.feature features/episodes/recall_logging.feature
bb spec spec/isaac/recall spec/isaac/episodes
```

## Exceptions

Feature files may only change by `@wip` removal unless a later planner note authorizes more.

## Implementation checkpoint (2026-09-17, scrapper@isaac-work-1)

Done:
- Moved `:floor-cos` schema/config resolution to `[:episodes :embedding :floor-cos]`; leftover `[:recall :floor-cos]` is ignored.
- Added the public live `select-injected` path with named selection constants; `inject-on-open!` consumes its search tiers and independent thread gists.
- Removed only the three authorized `@wip` tags.
- Pushed `bean/isaac-y3q6` at `dd6a137d2e011b9be2b10dd25c6c15c8983bc0d0`, based on `origin/main@5ca99b0ba8e27f1b1dfaea7dc28c86d28bb3c7ca`.
- Verification passed: focused scenarios 4/2/4 assertions; regression features 35 examples, 0 failures, 209 assertions; regression specs 209 examples, 0 failures, 563 assertions; `git diff --check` clean; edited production namespaces load successfully.

Next:
- Hand off to `isaac-verify` with reply-to `9aa11c51`; verifier resumes review at `src/isaac/recall/inject.clj:73` (`select-injected`) and `src/isaac/recall/inject.clj:187` (`inject-on-open!`).
