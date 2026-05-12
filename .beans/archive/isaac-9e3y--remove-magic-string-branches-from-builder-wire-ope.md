---
# isaac-9e3y
title: "Remove magic-string branches from builder; wire openai adapters to filter-fn"
status: completed
type: task
priority: normal
created_at: 2026-05-08T00:52:42Z
updated_at: 2026-05-08T04:01:48Z
---

## Description

isaac.llm.prompt.builder/build-messages currently branches on (= "openai" provider) to select the message filter function. This is a magic-string dispatch that belongs in the adapters, not the shared builder.\n\nWhat needs to change:\n- Make filter-messages and filter-messages-openai public exports from builder\n- Add :filter-fn option to build (default filter-messages), replace the (= "openai" provider) check\n- openai-completions and openai-responses pass :filter-fn prompt/filter-messages-openai\n- ollama, grover, claude-sdk drop the :provider assoc (use default filter)\n- Remove the unused prompt and anthropic-prompt requires from turn.clj (left over from earlier cleanup)

