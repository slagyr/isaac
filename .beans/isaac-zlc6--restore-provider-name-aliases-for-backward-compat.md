---
# isaac-zlc6
title: Restore provider name aliases for backward compat
status: scrapped
type: bug
priority: high
created_at: 2026-05-14T01:40:53Z
updated_at: 2026-05-14T01:43:00Z
---

Commit `75c24985` ("Finish manifest cleanup") removed the `canonical-provider` shim (one rule: `openai-codex → openai-chatgpt`) and dropped `:openai-codex` / `:openai-api` from the manifest, with no migration for existing state on disk.

## Symptom

Marvin's `tidy-comet` session on zanebot had `:provider "openai-codex"` written from earlier turns. Every dispatch errored out with `unknown api: openai-codex` (the error-message issue is its own bean). Session file was hand-patched on zanebot to unblock; other long-running sessions (discord-*, hook-*, other crews) can hit the same trap whenever they were last touched before the alias was dropped.

## Root cause

A user-visible name was removed from the resolver in the same commit that promoted the new name. The shim that bridged old→new was deleted at the same time. Effect: the on-disk format silently went incompatible. No `db migration`, no startup warning, no fallback.

## Acceptance

- Restore alias resolution in `isaac.llm.api/normalize` (single map, one point of truth — not scattered cond branches) covering at minimum:
  - `openai-codex → openai-chatgpt`
  - `openai-api → openai`
- Each alias resolution emits `log/warn :provider/deprecated-alias {:from "openai-codex" :to "openai-chatgpt"}` once per session (or rate-limited) so we can see the rot.
- Specs: dispatch with an old alias name resolves to the new provider AND emits the deprecation log.
- Decide and document the retirement plan: shim stays until we go N days with zero alias-hit logs, then either delete or write a one-time session-file migration. Leave a comment in the alias map noting this.

## Notes

- Closely related: `[[bug-dispatch-error-message-unknown-provider]]` — the error message that surfaced this issue is separately broken.
- Also related: `isaac-of7y` (startup config validation, still TODO) would have caught the unknown-provider state at boot instead of mid-chat.

## Todo

- [ ] Add alias map + log/warn in `isaac.llm.api/normalize`
- [ ] Specs: old alias name dispatches to new provider; deprecation log emitted
- [ ] Comment the retirement plan on the alias map


## Reasons for Scrapping

Aliases complicate resolution — they add a parallel name space that has to be kept in sync with the real one, scattered across logs, error messages, and every code path that touches provider names. Bringing them back is solving the wrong problem.

The right shape:

1. **`[[isaac-trxt]]` makes the failure legible.** When dispatch hits an unknown provider, the message tells the user the real name, lists known providers, and (cheap edit-distance) suggests the close match. No alias map needed — just a useful error.
2. **Failure happens at boot, not mid-chat.** `[[isaac-of7y]]` (startup config validation, still TODO) should also scan session files, not just config, for unknown provider refs. Hard-fail at boot with the same `unknown provider` diagnostic so the user fixes session edn files before sessions try to dispatch.
3. **Session files on disk that reference retired names get fixed by hand** (or the user wipes them). That's a one-time cost paid by whoever was on the old name. Fine.
