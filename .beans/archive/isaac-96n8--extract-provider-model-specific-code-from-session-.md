---
# isaac-96n8
title: "Extract provider/model-specific code from session.compaction"
status: completed
type: task
priority: normal
created_at: 2026-05-07T17:55:20Z
updated_at: 2026-05-07T22:37:53Z
---

## Description

isaac.session.compaction (post-merge from isaac.context.manager — see isaac-yfb4) constructs LLM requests directly and threads `model` + `provider` through several helpers. Compaction shouldn't know how each Api adapter serializes tools or how each model's request shape affects token estimation. Push that detail behind isaac.llm.api.

## Concrete coupling sites

1. **`compaction-request [model provider compacted]`** (currently lines 131-140):
   - Hard-codes the request map shape ({:model :messages :tools})
   - Calls `prompt/build-tools-for-request ... provider` to serialize tools per-Api
   These should be one Api method, e.g. `(api/build-summary-request api {:model ... :messages ... :memory-tools memory-tool-names})`, returning the Api-shaped request.

2. **provider-name threaded through chunk-plan / feasible-chunks / summarize-messages / chunked-response**:
   Used only for log lines and to be passed back into `compaction-request`. Once compaction-request stops constructing the wire shape, the name no longer needs to flow through these helpers — the Api instance is passed once at the boundary and the api/* methods own naming.

3. **`prompt/estimate-tokens` sized against Api-shaped requests**:
   The `chunk-budget` comment says "the provider rejects based on prompt size, so use the model window directly." That's a leaky assumption — token estimation should be an Api method (`(api/estimate-tokens api request)`) or accept a request that the Api produced.

4. **`invoke-chat-fn` arity probing (3 → 2 → 1 args)**:
   Compatibility cruft adjacent to the Api boundary. Converge on a single chat-fn signature; remove the probe.

## Terminology cleanup (lands with the extraction)

The code currently uses `provider` ambiguously — sometimes meaning an Api instance, sometimes a provider-name string (after `display-name` is called on it). The codebase has a canonical vocabulary:

- **`Api`** — the protocol in isaac.llm.api
- **`api`** — an Api instance (the variable name throughout this codebase)
- **`provider-name`** — the string identifier (`"anthropic"`, `"openai-codex"`, …) returned by `(api/display-name api)`
- **provider** — the upstream service the Api connects to (Anthropic, OpenAI, Ollama). Not a Clojure value.

Apply throughout the new isaac.session.compaction:

- Rename the parameter `provider` → `api` everywhere it holds an Api instance.
- Drop `provider-name` entirely — once the wire shape lives behind Api, no helper needs the string.
- The public fn `compact!` should accept `:api` in its options map (not `:provider`); update isaac.drive.turn to pass an Api instance directly.

## Goal

`compact!` reads as: "decide what to compact, ask the api to summarize it, splice the summary back in." It does not assemble request maps, does not know about Api tool-serialization formats, and does not see a provider-name string.

## Out of scope

- Changing the chunking algorithm or the system-prompt wording.
- Renaming `provider` → `api` outside isaac.session.compaction (other namespaces have their own cleanup needs; this bead stays focused).

## Acceptance Criteria

bb spec green; bb features green; isaac.session.compaction does not require isaac.prompt.builder; provider-name no longer threaded through compaction internals; the parameter name 'provider' does not appear in isaac.session.compaction (replaced by 'api' for Api instances); invoke-chat-fn's arity probe removed; existing compaction behavior unchanged (golden-path session keeps producing equivalent summaries); isaac.drive.turn passes an Api instance to compact! directly.

## Design

The Api protocol (isaac.llm.api) is the right abstraction line — it already owns provider-specific behavior (chat, chat-stream, followup-messages, display-name). Adding 1-2 methods (build-summary-request, estimate-tokens) is cheaper than continuing to thread provider-name + raw request maps through helpers that should be transport-ignorant. Resist creating a 'compaction adapter' layer; let the existing Api protocol grow.

Terminology cleanup rides along because the rename is a wash without it: extracting wire-shape duties out of compaction makes the surviving 'provider' parameter name actively misleading (it would only ever hold an Api instance now). Renaming during the extraction is cheaper than two passes.

