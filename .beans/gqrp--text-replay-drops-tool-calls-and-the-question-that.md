---
# gqrp
title: Text replay drops tool calls and the question that caused them
status: in-progress
type: bug
priority: high
created_at: 2026-09-15T19:35:28Z
updated_at: 2026-09-15T19:35:28Z
---

## Problem

The default prompt filter `isaac.llm.prompt.builder/filter-messages` (the text path used by ollama, grover, the claude-code module, and compaction's token estimate) was the April Ollama-era filter: it dropped every assistant tool-call message **and the user message immediately before a tool call**, and replayed tool results as bare user text. Found on yopp 2026-09-15: a second claude-code turn in session `test-cc-batch` answered "I haven't run any tools in this conversation" although the transcript held both calls and results.

## Fix (decision 2026-09-15, Micah: fix now, deploy ASAP)

Default filter replays the whole conversation as text — nothing dropped:
- tool calls → assistant `[tool call <name> <json args>]` lines (adjacent calls merge, one per line)
- results → user `[tool result]\n<text>` / `[tool error]\n<text>` (adjacent results merge, blank-line separated; truncation unchanged)
- text before a call in the same entry stays in front of the call lines

Clean cutover; no switch for the old behavior. OpenAI and Anthropic filters unchanged. Ollama keeps the text filter (moving it to the native tool-message chain is a separate, untested change).

## Acceptance

- `clojure -M:features features/llm/text_replay.feature` (proven red on 3e3ef7e)
- `bb spec spec/isaac/llm/prompt/builder_spec.clj`
- `bb ci` green
- zanebot: agent release deployed; yopp claude-code follow-up turn recalls prior tool output (yopp only if Micah asks)
