---
# isaac-aorj
title: "Provider schema: support :auth field + rename openai-codex to openai-chatgpt"
status: completed
type: bug
priority: normal
created_at: 2026-04-24T00:31:48Z
updated_at: 2026-04-24T01:59:12Z
---

## Description

The provider schema (src/isaac/config/schema.clj:78-109) lists :api-key, :auth-key, :token, etc. but NOT :auth. Provider EDN like {:auth "oauth-device" ...} has :auth silently dropped during schema validation. Downstream adapters (src/isaac/llm/openai_compat.clj:46) read :auth to branch on OAuth vs api-key flow; with :auth missing, they fall through to the api-key branch and emit the misleading error 'Missing OpenAI API key. Set OPENAI_API_KEY or configure provider :apiKey.' — even though the user's intent was OAuth and no env var is actually being consulted.

Fixes:
- Add :auth as a recognized provider schema field (string; valid values include 'oauth-device').
- Rename the built-in 'openai-codex' provider to 'openai-chatgpt' to match OpenAI's branding ('Sign in with ChatGPT'). 'codex' is an internal URL path, not the user-facing product.
- Introduce (or keep) 'openai-api' as the sibling provider name for plain api-key OpenAI access. Users pick based on auth style.
- Confirm the auth flow: isaac auth login --provider openai-chatgpt populates the OAuth token store; adapter reads via resolve-oauth-tokens.

Secondary: once :auth flows through, the correct error (Missing OpenAI Codex device login. Run Unknown provider: openai-chatgpt first.) must reference the new provider name.

Related bead (separate): isaac-??? fixes .env path resolution so  substitution can read from ~/.isaac/.env.

Acceptance:
1. :auth field present in provider schema; isaac config get providers shows :auth when set in EDN.
2. Provider 'openai-chatgpt' is the canonical name for the OAuth device-flow OpenAI provider; 'openai-codex' either renamed or aliased.
3. Provider 'openai-api' covers plain api-key OpenAI access.
4. Broken auth (missing OAuth tokens) produces 'Missing OpenAI ChatGPT login. Run isaac auth login --provider openai-chatgpt first.' — not the api-key error.
5. bb features and bb spec pass.

## Notes

Completed with bb spec green and bb features green. Provider schema now preserves :auth, the ChatGPT OAuth provider name is openai-chatgpt, openai-api covers API-key OpenAI access, and missing OAuth auth now points users at isaac auth login --provider openai-chatgpt.

