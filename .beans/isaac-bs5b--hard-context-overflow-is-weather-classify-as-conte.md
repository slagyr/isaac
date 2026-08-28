---
# isaac-bs5b
title: 'Hard context overflow is weather: classify as context-exhausted so hail defers, never 5× stuffing'
status: draft
type: bug
priority: high
created_at: 2026-08-28T16:04:05Z
updated_at: 2026-08-28T16:04:05Z
---

Likely repo: **isaac-agent** (`isaac.drive.provider-wall` + `execute-llm-turn!`). Hail should not change if the reason is reused.

## Observed (2026-08-28, zanebot tono-work-1)

xAI returned HTTP 400 `maximum prompt length is 500000 but request contains 505k–509k`. Isaac classified that as `:api-error`. Hail `reschedule!` burned 5 attempts (1s/5s/30s/2m/10m) and dead-lettered healthy hails (`tono-18ir` / `tono-j8r8` / `tono-lvkx`). Each retry is a new turn: `execute-llm-turn!` appends the hail prompt (~+498 tokens) *before* the LLM call, so retries stuffed the transcript that was already over the cap.

isaac-dark's pre-request guard did not fire: compaction was enabled and the local estimate sat under the 0.8 compact line (and under the 0.98 guard, which also requires `:compaction-disabled`). Token accounting (isaac-pqjn) is a separate undercount; this bean is the hail-retry disease.

## Design

Drive owns provider semantics (isaac-3tvq / isaac-5a4n). Hail already defers `{:unavailable? true :reason :context-exhausted}` with zero attempt burn and throttled attention (`isaac-hail` `defer-delivery!` + `maybe-notify-context-exhausted!`). Do **not** special-case `:api-error` in hail.

1. **Classify overflow as weather** in `isaac.drive.provider-wall/classify`, after auth and wall. Match the provider *message* (body or top-level), not bare HTTP 400 — a generic 400 stays `:api-error`. Phrases (case-insensitive): `maximum prompt length`, `prompt is too long`, `maximum context length`, `context_length_exceeded`, `context length exceeded`. Result: `{:unavailable? true :reason :context-exhausted :retry-after-ms <auth-tier default 300000> :provider ...}` plus `:message` when present. Log `:warn :chat/provider-context-exhausted` (distinct from `:drive/context-exhausted`, which is the pre-request guard).
2. **Reuse `:context-exhausted`.** New reason would need hail attention wiring. Same disease as isaac-dark; same parking + paging.
3. **Do not leave the rejected user turn on the transcript.** `execute-llm-turn!` appends the user message before the request (`when-not (:from-queue? charge)`). If that request classifies as `:context-exhausted`, remove the message this turn just appended. Otherwise deferred retries (every 5 min, never-die) re-append and stuff slowly — worse than dead-lettering after five copies. No pop API today; add the smallest store helper. Queue turns (`:from-queue?`) already persisted the user message upstream — do not double-drop.
4. **Hail: no code.** Existing `unavailable` → `defer-delivery!` is the contract. A hail scenario that queues grover `http-error` (not pre-classified `unavailable`) proves the stack.

Out of scope: forcing compaction on this error; changing the 0.8 threshold; LaunchAgent env (separate).

## Decision (2026-08-28, Micah)

Approved drafting this bean (overflow must not retry as generic `:api-error`).

## Decision (2026-08-28, planner)

Reuse `:context-exhausted` + rollback the just-appended user message. Reason reuse so hail attention fires without hail changes. Rollback so never-die deferral cannot stuff.

## Scenarios (to draft before todo)

See chat scenario plan. Stays draft until `@wip` features exist.

## Notes

- Grover `http-error` already carries `:status` / `:message`; real `isaac.llm.http` 400s put the text on `:body :error :message`. `response-message` already reads both.
- `isaac service install` dropping plist env is a different bean.
