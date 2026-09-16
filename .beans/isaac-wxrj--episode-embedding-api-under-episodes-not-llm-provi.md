---
# isaac-wxrj
title: Episode Embedding API under :episodes, not LLM providers
status: draft
type: task
priority: normal
tags:
    - episodes
created_at: 2026-09-16T17:02:33Z
updated_at: 2026-09-16T17:07:53Z
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
Runnable commands land when `@wip` scenarios are committed (see Next).

## Next
Scenario plan in planning chat — draft until those are approved and committed.


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
