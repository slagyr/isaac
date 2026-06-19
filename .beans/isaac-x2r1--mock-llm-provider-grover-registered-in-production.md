---
# isaac-x2r1
title: Mock LLM provider 'grover' registered in production (isaac.llm.api.grover)
status: in-progress
type: bug
priority: normal
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-19T22:38:29Z
---

Boot logs :api/registered {:api "grover"}. grover is NOT in config — it's a
MOCK/TEST LLM provider baked into isaac-agent (src/isaac/llm/api/grover.clj:
scripted responses, "grover exception", "tc_grover"/"fc_grover" simulated tool
calls). It's registered UNCONDITIONALLY alongside the real APIs (ollama,
chat-completions, responses), so every production server exposes it.

Fix: gate the grover test provider out of production — register it only under a
test/dev flag (or in test fixtures), not in the default API set. (isaac-agent)
