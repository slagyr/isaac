---
# isaac-4ca2
title: "LLM reorg A: rename namespaces + move Api protocol + conform registry to keyword"
status: completed
type: task
priority: normal
created_at: 2026-05-07T05:28:18Z
updated_at: 2026-05-07T16:08:32Z
---

## Description

Foundation move under umbrella isaac-uwso. No behavior change.

Renames:
- isaac.provider → isaac.llm.api (Api protocol + registry; today's 'Provider' protocol becomes 'Api')
- isaac.llm.<name> → isaac.llm.api.<api-id>:
  - isaac.llm.anthropic → isaac.llm.api.anthropic-messages
  - isaac.llm.ollama → isaac.llm.api.ollama
  - isaac.llm.grover → isaac.llm.api.grover
  - isaac.llm.claude-sdk → isaac.llm.api.claude-sdk
  - isaac.llm.openai-compat stays for now (split happens in sub-bead B)
- 'Provider' protocol/symbol → 'Api'

Other:
- Conform registry param to keyword: register! / unregister! / factory-for / registered-apis docstrings already say 'keyword', but callers pass strings. Add an internal ->api coercion (string|keyword → keyword), normalize at each entry point. Decide whether resolve-api also returns keyword (probably yes — otherwise factory-for has to coerce on every lookup).
- Update isaac.drive.dispatch and any consumer that imports isaac.provider or isaac.llm.<name>.

Specs/docstrings/comments updated accordingly. Module-facing isaac.api stays put.

## Acceptance Criteria

isaac.provider gone (or trivially re-exporting from isaac.llm.api during deprecation); all four impl namespaces renamed; Api protocol replaces Provider; registry keyed by keyword; bb spec and bb features green.

## Notes

Verification failed: acceptance text still not met. Old isaac.provider consumers remain in specs (for example spec/isaac/bridge/chat_cli_spec.clj:16, spec/isaac/llm/anthropic_spec.clj:8, spec/isaac/module/provider_test.clj:4-8), and docstrings/comments still refer to Provider rather than Api (src/isaac/bridge.clj:268-269, src/isaac/drive/turn.clj:496-498, src/isaac/llm/api.clj:91-92). bb spec and bb features both pass.

