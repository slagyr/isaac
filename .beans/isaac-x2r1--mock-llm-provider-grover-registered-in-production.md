---
# isaac-x2r1
title: Mock LLM provider 'grover' registered in production (isaac.llm.api.grover)
status: completed
type: bug
priority: normal
tags: []
created_at: 2026-06-19T22:22:26Z
updated_at: 2026-06-19T22:53:51Z
---

Boot logs :api/registered {:api "grover"}. grover is NOT in config — it's a
MOCK/TEST LLM provider baked into isaac-agent (src/isaac/llm/api/grover.clj:
scripted responses, "grover exception", "tc_grover"/"fc_grover" simulated tool
calls). It's registered UNCONDITIONALLY alongside the real APIs (ollama,
chat-completions, responses), so every production server exposes it.

Fix: gate the grover test provider out of production — register it only under a
test/dev flag (or in test fixtures), not in the default API set. (isaac-agent)

## Verification Notes

- Verification passed on 2026-06-19 against fetched GitHub `isaac-agent` `main` at `2a20c01`, not the stale local `../plan` mirror.
- Focused proof passed: `env ISAAC_GIT=1 bb spec spec/isaac/slash/registry_spec.clj spec/isaac/llm/api_spec.clj` -> `47 examples, 0 failures, 61 assertions`.
- Companion touched-helper proof also passed: `env ISAAC_GIT=1 bb spec spec/isaac/session/session_steps_spec.clj` -> `2 examples, 0 failures, 7 assertions`.
- The production gate is in [src/isaac/llm/api/protocol.clj](/Users/micahmartin/agents/verify/isaac-agent/src/isaac/llm/api/protocol.clj:189): berth registration now skips `:grover` unless Grover test registration is explicitly enabled.
- The test fixture remains available in [src/isaac/llm/api/grover.clj](/Users/micahmartin/agents/verify/isaac-agent/src/isaac/llm/api/grover.clj:390), and [spec/isaac/llm/api_spec.clj](/Users/micahmartin/agents/verify/isaac-agent/spec/isaac/llm/api_spec.clj:149) proves `:isaac.agent` activation no longer registers `:grover` outside test mode while the helper-based specs still install it when needed.
