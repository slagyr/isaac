---
# isaac-lrqo
title: Ollama adapter must send options.num_ctx from :context-window (server default 4096 truncates isaac prompts)
status: draft
type: bug
priority: high
created_at: 2026-08-26T16:05:22Z
updated_at: 2026-08-26T16:05:22Z
---

Likely repo: **isaac-agent** (`src/isaac/llm/api/ollama.clj`). Found 2026-08-26
moving keaton onto a local ollama model on zanebot.

## Problem

`ollama.clj` posts `request` to `/api/chat` with only `:stream`/`:think`
added. It never sends `options.num_ctx`, so ollama runs the model at its
**server default context (4096)** regardless of the model entry's
`:context-window`. `curl localhost:11434/api/ps` on zanebot shows
`context_length 4096` for qwen2.5:14b and llama3.2 while isaac's model
entries say 32768.

isaac's prompt (soul + rules + tool schemas + history) is far larger than
4K, so ollama silently truncates it. Observed:

- `qwen2.5:14b` via `isaac prompt … --crew pinky` (6 tools) →
  `empty-terminal-response` every time; raw curl with the same user
  message answers `qwen-pong`.
- `llama3.2` (thinking disabled) → hallucinated tool-call JSON as content.
- The same models via curl with a 7K-token system prompt and no `num_ctx`
  show `prompt_eval_count 2050` — ollama kept a fraction of the prompt.

Separately: `models/default.edn` (`llama3.2`) and `qwen3.edn` lack
`:allows-effort false`, so isaac sends `think: true` and ollama 400s with
`"llama3.2" does not support thinking`. `qwen3.edn` also points
`qwen3-coder:30b` at `:ollama` (localhost) where it is not installed — it
lives on nightbird (`providers/ollama-nightbird.edn`).

## Design

- `chat` / `chat-stream` add `:options {:num_ctx <context-window>}` when the
  resolved model cfg has `:context-window`; merge with any `:options` the
  model entry already carries (`:num_ctx` explicit wins).
- Think flag: only send `:think` when the model entry opts in
  (`:allows-effort true` or explicit `:think-mode`); default off for ollama —
  most local models reject it.
- Log the effective `num_ctx` at `:chat/request` for ollama so a truncation
  mismatch is visible.

## Scenarios (to write before todo — features/llm/api/ollama*.feature)

- model entry `:context-window 32768` → outbound `/api/chat` body has
  `options.num_ctx 32768`.
- model entry with explicit `:options {:num_ctx 8192}` → that value wins.
- model entry without `:allows-effort` → no `think` key in the body.

## Ops notes (zanebot, 2026-08-26)

- Added `models/qwen25.edn` (`qwen2.5:14b`, `:allows-effort false`,
  `:context-window 32768`) and `models/llama32.edn`; keaton was switched to
  `:qwen25`, found broken by this bug, and **reverted to `:grok`** pending
  the fix. Micah's intent: keaton (personal finance, private data) runs on
  local ollama.
- Reloading qwen2.5:14b at 32K context on zanebot is slow — check memory
  headroom before choosing num_ctx there; nightbird carries the larger
  models.
