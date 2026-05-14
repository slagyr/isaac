---
# isaac-trxt
title: 'Dispatch error ''unknown api: X'' should be ''unknown provider: X'' with did-you-mean'
status: completed
type: bug
priority: normal
created_at: 2026-05-14T01:41:07Z
updated_at: 2026-05-14T14:18:54Z
---

When `make-provider` can't resolve a provider name, it returns an `UnknownApiProvider` (src/isaac/drive/dispatch.clj:13-20) whose `chat`/`chat-stream` emit:

```clojure
{:error :unknown-api, :message "unknown api: openai-codex"}
```

This is wrong-shape from a user POV.

## Why

The user writes `:provider "..."` in session/crew configs. They never see or write `:api` directly — that's the internal wire dialect (`openai-responses`, `anthropic-messages`, etc.) selected via the provider's `:api` field. Emitting an "api" error for a provider lookup failure:

1. Uses vocabulary the user didn't choose (`api`).
2. Gives no path forward — no list of known providers, no suggestion.
3. Masks the actual failure mode (provider name vs. provider-known-but-misconfigured).

## Acceptance

- Replace `:error :unknown-api` with `:error :unknown-provider` (or emit both, but `:unknown-provider` is the user-facing one).
- Message: `unknown provider "<name>" — known: anthropic, ollama, openai, openai-chatgpt`. Known list comes from manifest + module index, not hardcoded.
- "Did you mean X?" suggestion when there's an obvious match (cheap edit distance only — no alias map).
- Distinguish from "provider known but unusable" (missing auth, base-url, etc.) — those should have their own diagnostic with a different `:error` keyword. For this bean: just make sure the unknown-name path is right; misconfig path can be a follow-up.
- Specs cover: unknown name (with did-you-mean), unknown name (no close match), known name missing required config.

## Todo

- [x] Refactor `UnknownApiProvider` to take known-providers list at construction
- [x] Cheap edit-distance helper (Levenshtein ≤ 2 or similar); no alias map
- [x] Rename `:error :unknown-api` → `:error :unknown-provider` (sweep call sites)
- [x] Specs

## Notes

- Discovered while debugging Marvin's failing chat on zanebot. The band-aid was patching the session file; this bean is what makes the failure mode legible next time.
- `[[isaac-zlc6]]` (alias restoration) was the first proposal and got scrapped — aliases complicate resolution. This bean is the actual fix: make unknown-provider failures legible, period.

## Summary of Changes

- Added `levenshtein` and `did-you-mean` helpers in `dispatch.clj`
- Refactored `UnknownApiProvider` to take `known-providers` list instead of `api-name`; emits `:unknown-provider` with message including known list and optional did-you-mean suggestion
- `make-provider` now builds the known-providers list from manifest + module index via `providers/known-providers` + `providers/module-providers`
- Added 4 new specs covering: unknown name error shape, did-you-mean match, no close match, known list contents
