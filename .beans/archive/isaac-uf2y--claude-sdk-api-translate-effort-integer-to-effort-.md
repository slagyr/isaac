---
# isaac-uf2y
title: "Claude SDK API: translate effort integer to --effort CLI flag"
status: completed
type: task
priority: normal
created_at: 2026-05-12T04:59:02Z
updated_at: 2026-05-12T04:59:57Z
---

## Description

The claude_sdk adapter (claude_sdk.clj) calls the claude CLI but build-args ignores the :effort key from the request. The claude CLI supports --effort with levels: low, medium, high, xhigh, max.

## What to do

1. Add effort->claude-level translation in claude_sdk.clj:
   - 0: omit --effort flag (no thinking)
   - 1-2: low
   - 3-4: medium
   - 5-6: high
   - 7-8: xhigh
   - 9-10: max

2. Update build-args to read :effort from request and add --effort <level> when non-zero.

3. Add a feature file features/llm/api/claude_sdk.feature with scenarios mirroring the anthropic/ollama pattern (effort 0 omits flag, effort 2 → low, effort 5 → high, effort 10 → max).

4. Add unit specs covering the effort->claude-level mapping.

## Acceptance
- build-args with effort 0 does not include --effort
- build-args with effort 2 includes --effort low
- build-args with effort 10 includes --effort max
- Feature scenarios pass

