---
# isaac-goq9
title: "Push prompt building behind the Api protocol"
status: completed
type: feature
priority: normal
created_at: 2026-05-07T19:30:06Z
updated_at: 2026-05-08T04:42:39Z
---

## Description

Today prompt building is dispatched at the call site: isaac.drive.turn:481-482 chooses between `anthropic-prompt/build` and `prompt/build`. Inside `prompt.builder`, further branching keys on string heuristics: `(= "openai" provider)` selects the message filter, `codex-provider?` (`str/ends-with? ... "openai-codex"`) picks the tool format. Provider knowledge is scattered across three places — the drive, the builder's branches, and the dedicated anthropic builder.

After this work, each Api adapter owns its prompt-building responsibility and the drive becomes provider-ignorant:

```
;; Today (drive/turn.clj)
(if anthropic-style?
  (anthropic-prompt/build opts)
  (prompt/build opts))

;; After
(api/build-prompt api opts)
```

## Goal

- Add `build-prompt` to the Api protocol in isaac.llm.api.
- Each isaac.llm.api.<name> implements `build-prompt`, delegating to helpers in isaac.llm.prompt.* (post isaac-7xtd) for shared concerns.
- Drive/turn stops dispatching; just calls `(api/build-prompt api opts)`.
- Magic-string branches inside isaac.llm.prompt.builder go away — each adapter routes to the right helpers.
- isaac.llm.prompt.anthropic stops round-tripping through builder.

## Tasks

The worker tackles these in order within this single bead. Each is independently shippable as its own commit; the bead closes when all are done and acceptance criteria pass. The protocol default impl in step 1 keeps the build green throughout.

1. Add `build-prompt` to the Api protocol with a sensible default impl (delegates to current isaac.llm.prompt.builder/build for now). All adapters compile without changes.
2. Implement `build-prompt` for isaac.llm.api.anthropic — calls into isaac.llm.prompt.anthropic, no longer round-trips through builder.
3. Implement `build-prompt` for isaac.llm.api.openai-completions — calls into a dedicated openai-completions helper (extracted from the current `(= "openai" provider)` branches in builder).
4. Implement `build-prompt` for isaac.llm.api.openai-responses — same pattern; covers Codex/ChatGPT tool-format branch currently behind codex-provider?.
5. Implement `build-prompt` for isaac.llm.api.ollama — calls into the Ollama-style filter (current default-branch behavior in builder/filter-messages).
6. Implement `build-prompt` for isaac.llm.api.grover — test stub; delegates as needed.
7. Update isaac.drive.turn to call `(api/build-prompt api opts)`; remove the dispatch.
8. Remove the magic-string branches from isaac.llm.prompt.builder (filter selection, codex-provider?). What remains is genuinely shared utility.
9. Verify: each adapter's wire output matches today's behavior (regression specs).

## Why this is substantial

Touching every Api adapter, the drive, and both prompt builders. Per-adapter task ordering lets verification happen incrementally — one adapter at a time — rather than landing as one all-or-nothing diff.

## Out of scope

- Consolidating compaction-aware transcript walking (that's separate, follows isaac-15p4).
- Lifting estimate-tokens / truncate-tool-result to other homes.
- Changing wire formats — every adapter produces the same request shape it produces today.

## Acceptance Criteria

Each isaac.llm.api.<name> implements build-prompt; isaac.drive.turn:481-482 contains no anthropic vs builder dispatch; isaac.llm.prompt.builder contains no provider-string branches (no equality check on "openai" and no codex-provider? helper); isaac.llm.prompt.anthropic does not round-trip through builder; bb spec and bb features green; existing wire-format specs continue to pass.

## Design

The Api protocol is the right place to localize provider-aware prompt construction. Today three places share that knowledge (drive, builder, anthropic). After this, only the adapter knows. Resist creating per-adapter prompt namespaces (isaac.llm.prompt.openai, isaac.llm.prompt.ollama, etc.) — keep helpers shared in isaac.llm.prompt.* and let adapters compose them. The dispatch boundary is the protocol; the implementation is shared utility.

## Notes

Verification failed: unit specs are green, but the bead does not meet its own acceptance criteria. src/isaac/llm/prompt/anthropic.clj still round-trips through builder.build in extract-messages, even though the bead explicitly required that isaac.llm.prompt.anthropic not round-trip through builder. Current main also has 2 failing feature scenarios in features/session/identity.feature, but those appear related to later session-store work rather than this prompt-building bead.

