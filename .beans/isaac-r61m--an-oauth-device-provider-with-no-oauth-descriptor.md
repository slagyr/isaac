---
# isaac-r61m
title: 'An oauth-device provider with no :oauth descriptor fails every turn with "Unknown oauth provider descriptor: " (blank) once its token needs a refresh'
status: todo
type: bug
priority: high
created_at: 2026-09-24T20:26:19Z
updated_at: 2026-09-24T20:26:19Z
---

Micah, 2026-09-24, on his laptop's own isaac root: session `clever-signal` (crew marvin, model gpt-5.4 on provider `openai-codex`) ended `:error` with `Unknown oauth provider descriptor: ` — nothing after the colon.

## Cause
`providers/openai-codex.edn` is `{:base-url "https://api.openai.com/v1" :auth "oauth-device" :api "responses"}` — no `:oauth` key, and the provider is not named `chatgpt`, so `isaac.llm.api.openai.shared/oauth-descriptor` returns nil. That is fine while the stored token is fresh. The first time `auth-store/token-needs-refresh?` is true, `refresh-oauth-tokens!` calls `device-code/refresh-tokens! nil …` and `provider-descriptor!` throws with a nil provider, so the message is blank and the turn dies as a plain exception (not auth weather).

## Fix (isaac-agent)
- Descriptor resolution for `oauth-device` providers: `:oauth` on the provider, else by provider name (`chatgpt`, `grok`), else by `:api`/`:base-url` family (openai `responses`/`chat` → chatgpt descriptor; x.ai → grok). Document the rule on the provider schema.
- If none resolves: a **config validation error** at load (`providers.<name>: :auth "oauth-device" needs an :oauth descriptor (chatgpt|grok|{...})`), not a runtime exception; and the runtime path names the provider (`… for provider openai-codex`) if it is ever reached.
- A refresh failure is auth weather (isaac-ugpq family): park the turn with attention, do not end it `:error`.
- `isaac auth login --provider <name>` should accept any configured oauth-device provider, not only the hard-coded set `#{chatgpt grok …}` (`known-providers`), so a provider named `openai-codex` can be logged in at all.

## Acceptance
- [ ] Spec: provider `{:auth "oauth-device" :api "responses"}` named anything resolves the chatgpt descriptor; a provider with none resolvable is a validation error naming the provider.
- [ ] Scenario: a turn whose oauth-device token needs refresh and whose descriptor is missing ends parked with attention text naming the provider, never `Unknown oauth provider descriptor: `.
- [ ] `isaac auth login --provider openai-codex` works on a config that declares it.
- [ ] `bb ci` green.

Repo scope: isaac-agent (`llm/auth/device_code.clj`, `llm/auth/store.clj`, `llm/api/openai/shared.clj`, `llm/auth/cli.clj`, provider schema).
