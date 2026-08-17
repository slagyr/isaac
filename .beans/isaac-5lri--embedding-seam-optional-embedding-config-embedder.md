---
# isaac-5lri
title: 'Embedding seam: optional :embedding config + Embedder protocol + ollama adapter + isaac embed CLI'
status: in-progress
type: task
priority: normal
created_at: 2026-08-17T01:09:17Z
updated_at: 2026-08-17T01:14:47Z
parent: isaac-51xy
---

Child of isaac-51xy (episodic memory). First bean of phase 1: the embedding seam — an OPTIONAL embedding capability behind a protocol, exercised by a minimal `isaac embed` CLI. No scenes, no index — just the seam.

## Design (settled 2026-08-16, Micah + planning session)
- **Namespace:** `isaac.recall.embedding` (protocol + config resolution) and `isaac.recall.embedding.ollama` (adapter). Recall is a PEER of conversation, crew-level — not nested under it (isaac-51xy Decision 18). New `Embedder` protocol; the chat-shaped `api/Api` protocol is NOT extended.
- **Protocol is batch-shaped:** `(embed this texts) → vectors`, one vector per text, order preserved — the index backfill embeds many scenes per call (ollama /api/embed takes batch `input`).
- **Config:** root-level `:embedding` key (schema contributed via manifest), a discriminated union on `:source`. Only variant in this bean:
  `{:embedding {:source :provider :provider "ollama" :model "nomic-embed-text"}}`
  Separate `:provider`/`:model` keys — NO "provider:model" ref strings (ollama model names contain colons, e.g. qwen3-coder:30b; grover simulation adds another). `:provider` resolves through the existing `:providers` config for connection details; `:model` is the wire string. **Embedding models never enter `:models`** — different category from chat models, not interchangeable.
- **Absence of `:embedding` is legal** (Base/Remembering tier): `config validate` passes; `isaac embed` degrades to a helpful pointer, no stack trace.
- **Present-but-broken is a config error** in the house validation dialect (`references undefined provider`, `bad value:`, `valid:`), hot-reloaded per no-service-restarts.
- **Grover embed contract (test stub):** grover satisfies `Embedder` deterministically with 4-dim integer vectors `[char-count char-sum first-char-code last-char-code]` — "hello" → [5 532 104 111]. Exact values assertable in features; distinct inputs give distinct vectors.
- **Ollama adapter:** POST `{base-url}/api/embed` with `{model, input}`, reusing `isaac.llm.http`; base-url from resolved provider config (default http://localhost:11434).
- **CLI:** `isaac embed [options] [text ...]` — one argument = one TEXT (not word; quoting supported), one vector line per argument. Debug/eval tool; becomes part of the retrieval-checkpoint tooling.

## Scenarios
Committed @wip in isaac-agent: `features/recall/embedding.feature` (7 scenarios, all steps reused — zero new steps; step adequacy verified against features/config/cli.feature and features/llm/api/ollama/api.feature).

## Acceptance
- `bb features features/recall/embedding.feature` passes (all 7, @wip removed on completion)
- `bb features` (full suite) stays green

## Implementation notes / assumptions to verify
- The `isaac is run with` harness shares process state with grover's request recorder (as `the user sends` does). If it's a subprocess, switch scenario 5's When to an in-process variant; assertions unchanged.
- Quoted-argument parsing in `isaac is run with "embed \"hi there\" ..."` — if unsupported, adjust fixture, keep the one-arg-one-text assertion.
- `the last outbound HTTP request matches:` cell dialect (substring vs exact, array rendering) — adjust cells to the step's dialect, not the semantics.
- Exact validation-message wording may be tuned to the schema system's output; keep the path-anchored `embedding.provider` / `bad value:` shape.
