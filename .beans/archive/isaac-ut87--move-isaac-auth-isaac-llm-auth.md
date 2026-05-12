---
# isaac-ut87
title: "Move isaac.auth → isaac.llm.auth"
status: completed
type: task
priority: normal
created_at: 2026-05-07T17:11:10Z
updated_at: 2026-05-07T17:35:15Z
---

## Description

Auth code under isaac.auth is exclusively LLM-provider authentication (Anthropic API keys, OpenAI/Codex device-code OAuth, Ollama). It belongs under the isaac.llm umbrella alongside the rest of the provider machinery (registry, http, providers catalog, tool-loop).

Today isaac.llm.api.openai.shared already requires isaac.auth.store, which crosses top-level namespace boundaries. Moving auth under isaac.llm naturalizes that dependency and groups all provider concerns together.

Changes:

1. Namespace + file move:
   - src/isaac/auth/store.clj       → src/isaac/llm/auth/store.clj
   - src/isaac/auth/device_code.clj → src/isaac/llm/auth/device_code.clj
   - src/isaac/auth/cli.clj         → src/isaac/llm/auth/cli.clj
   - spec/isaac/auth/store_spec.clj       → spec/isaac/llm/auth/store_spec.clj
   - spec/isaac/auth/device_code_spec.clj → spec/isaac/llm/auth/device_code_spec.clj
   - spec/isaac/auth/cli_spec.clj         → spec/isaac/llm/auth/cli_spec.clj
   Update each ns form accordingly.

2. Update external requires (3 sites):
   - src/isaac/main.clj               (isaac.auth.cli → isaac.llm.auth.cli)
   - src/isaac/llm/api/openai/shared.clj (isaac.auth.store → isaac.llm.auth.store)
   - spec/isaac/llm/openai_responses_spec.clj (same)

3. Update internal cross-references inside the moved files (auth.cli → auth.device-code, auth.store).

4. Remove the now-empty src/isaac/auth and spec/isaac/auth directories.

No behavior change. CLI entry point (`isaac auth login`) and on-disk auth.json format stay the same.

## Acceptance Criteria

bb spec green; bb features green; grep -rn 'isaac\.auth' src spec returns only isaac.llm.auth.* references; src/isaac/auth and spec/isaac/auth removed; isaac auth login still works.

## Design

Trade-off considered: if non-LLM auth ever appears (Discord tokens, MCP creds, user sessions), isaac.llm.auth becomes too narrow. Accepted as hypothetical — today every caller is LLM-provider auth. Easy to lift back out if that changes.

