---
# isaac-gqrp
title: Text replay drops tool calls and the question that caused them
status: completed
type: bug
priority: high
created_at: 2026-09-15T19:35:28Z
updated_at: 2026-09-15T19:41:48Z
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

## Delivered (2026-09-15)

- isaac-agent `6b41e63` (squash), release 0.1.70 `2ed58f77d33d3f0ee72cb840401f793c97eba5e2`; registry pin `ee0443df`.
- Proven red on 3e3ef7e (old filter replayed "main up" where "hoist the sails" belonged); `bb ci` green: 1626 specs, 763 features, 0 failures. One existing scenario (context_management "Large tool results are truncated in prompts") moved its assertion from messages[1] to messages[3]; the question now precedes the result.
- zanebot deploy 19:38:40Z. `modules upgrade` refused again in the live root (isaac-784x); upgraded via a rehearsal root instead, and the isaac.edn diff was the agent sha only. Boot: 401, runner 8 components, resume requeued 1 + hail/bound, discord ready, no validation errors, no stale-delivery removals.
- Smoke on zanebot `claude-cli`: turn 1 ran `echo kite-292862244`; turn 2, with no tools, answered "I ran `echo kite-292862244`, which printed `kite-292862244`."
- yopp not upgraded (deploys skip yopp unless asked).
