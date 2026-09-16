---
# isaac-wxrj
title: Episode Embedding API under :episodes, not LLM providers
status: in-progress
type: task
priority: normal
tags:
    - unverified
    - episodes
created_at: 2026-09-16T17:02:33Z
updated_at: 2026-09-16T19:21:39Z
---

## Problem
Episode embeddings reuse the LLM `:providers` table via
`:embedding {:source :provider :provider "…" :model "…"}`.
`provider-embedder` then treats any non-ollama `:api` as an Ollama client
(default `http://localhost:11434/api/embed`). Embedding vendors (OpenAI,
Voyage, …) cannot be configured without that trap, and they do not belong
in the chat provider catalog.

## Design (2026-09-16, Micah)
Clean cutover. Embedding is episodes-only; all config lives under `:episodes`.
No `:source`, no LLM `:provider` id.

```
:episodes
{:embedding {:api     "embeddings"
             :model   "text-embedding-3-large"
             :base-url "https://api.openai.com/v1"
             :api-key "${OPENAI_ZANE_EMBEDDING_API_KEY}"}}
```

- **Embedding API** — multimethod on `:api`, not a protocol. Chat keeps
  `Api` objects because a turn needs many methods on a bound connection;
  embed is one function over this map.
- **Berth** `:isaac.session.episodes/embedding-api` (episodes owns embedding;
  other modules contribute by loading a ns that `defmethod`s).
- **Built-in methods:** `ollama` (`POST {base}/api/embed`), `embeddings`
  (OpenAI-compatible `POST {base}/embeddings` → `data[].embedding`), `grover`
  (deterministic stub).
- **Unknown `:api`** → config error, not silent Ollama.
- **No second provider catalog.** One map. Voyage later = same `embeddings`
  API with a different `:base-url` / key, or another `defmethod` if the wire
  differs.
- **`:model` required** (index identity). Live/example model is
  `text-embedding-3-large` (not small).
- Drop root `:embedding` and `check-embedding-provider` (LLM provider
  existence). Schema moves under `:episodes :embedding`; `:api` validates
  `[:registered-in? :isaac.session.episodes/embedding-api]`.
- Auth is explicit `:api-key` with `${OPENAI_ZANE_EMBEDDING_API_KEY}` — do
  not derive `OPENAI_API_KEY` from a chat provider name.

## Likely repo scope
isaac-episodes (multimethod, berth, methods, schema, fixtures).
Small isaac-agent grover stub for `POST …/embeddings` so features can capture
the request without a live call.

## Acceptance
isaac-episodes (remove `@wip`; all must pass):

    bb features features/recall/embedding.feature:17
    bb features features/recall/embedding.feature:30
    bb features features/recall/embedding.feature:43
    bb features features/recall/embedding.feature:55
    bb features features/recall/embedding.feature:69
    bb features features/recall/embedding.feature:83
    bb features features/recall/embedding.feature:104
    bb features features/recall/embedding.feature
    bb spec
    bb ci

Also rewrite every other episode feature/spec that still uses root
`{:embedding {:source :provider …}}` to `{:episodes {:embedding {:api "grover" :model "mini-embed"}}}`.
Unknown `:source` / unknown LLM-provider embedding scenarios are deleted (clean cutover).

## Scenario verdicts (2026-09-16, Micah)
embedding.feature — keep/rewrite/new as landed `@wip`:
1. help — rewrite (provider → API)
2. unconfigured — rewrite (error names `:episodes` `:embedding`)
3. grover embed hello — rewrite (new config)
4. batch — rewrite (new config)
5. ollama HTTP — rewrite (`:api "ollama"` + simulate-provider)
6. embeddings HTTP — new (bearer, text-embedding-3-large)
7. unknown api — new (replaces unknown provider/source)


## Decision (2026-09-16, Micah)
Embedding API is a **multimethod**, not a protocol. Dispatch value is
`:api` on the `:episodes :embedding` map. No long-lived adapter object —
LLM `Api` is a protocol instance because chat has many operations and
a bound connection; embed is one function over config.

`(defmulti embed (fn [embedding-cfg texts] (keyword (:api embedding-cfg))))`
with `defmethod` per API (`:ollama`, `:embeddings`, `:grover`). A new wire
format is another `defmethod` in a loaded ns (berth still lists api ids for
`registered-in?` and ns load). Unknown `:api` is an error method, not
Ollama. Drop `Embedder` / `resolve-embedder` returning an object.

## Worker checkpoint (2026-09-16, scrapper@isaac-work-2)

Done: implemented the episodes-owned embedding multimethod and built-in `grover`, `ollama`, and OpenAI-compatible `embeddings` methods; moved schema/config to `:episodes :embedding`; declared the embedding API berth; removed the legacy protocol, provider resolver, and provider validation check; rewrote specs/features to the clean-cutover config. Pushed WIP commit `49e7703`. Full `bb spec` is green: 206 examples, 0 failures, 558 assertions.

Current state: `features/recall/embedding.feature` runs 7 examples with 3 failures. Grover/help/unconfigured/batch pass. Ollama and embeddings commands exit before recording HTTP requests because feature runtime does not load the contributed API namespaces. Unknown API validation also does not see the new berth yet in feature composition.

Next: wire berth contribution namespaces into the feature runtime/module activation (or eagerly require built-ins at the episodes module boundary), then resume at `features/recall/embedding.feature:65`. Rerun `bb features features/recall/embedding.feature`, then full features/CI. The bean also calls for the agent Grover stub to support `/embeddings`; implement that in an isolated isaac-agent worktree after the episodes feature reaches the HTTP seam.

## Worker completion (2026-09-16, scrapper@isaac-work-2)

Implemented the clean cutover in two repositories.

- `isaac-episodes`: branch `bean/isaac-wxrj` @ `fb01bd3` (base `origin/main@60cb51b`). Added the `:isaac.session.episodes/embedding-api` berth and `:api` multimethod, built-in grover/ollama/OpenAI-compatible embeddings methods, nested schema under `:episodes :embedding`, explicit bearer auth, API namespace loading, complete fixture migration, and removal of the old protocol/provider resolver/check. Pinned foundation `d0b5ff5` so threaded CLI validation retains load errors, and agent `cd9361c` for the Grover `/embeddings` response.
- `isaac-agent`: branch `bean/isaac-wxrj` @ `cd9361c` (base `origin/main@3e6bf39`). Grover now returns OpenAI-compatible `data[].embedding` for `/embeddings`, with focused coverage.

Evidence:
- `isaac-episodes bb spec`: 206 examples, 0 failures, 558 assertions.
- `isaac-episodes bb features features/recall/embedding.feature`: 7 examples, 0 failures, 23 assertions.
- `isaac-episodes bb ci`: spec green and 82 feature examples, 0 failures, 529 assertions.
- `isaac-agent bb spec spec/isaac/llm/http_spec.clj`: 31 examples, 0 failures, 82 assertions.
- Full isaac-agent spec reached 1629 examples with one unrelated, known flaky `session_steps_spec.clj:139` timing failure; focused HTTP coverage is green.
- `git diff --check` clean; no legacy root/provider embedding references remain in episodes source/spec/features/resources.
