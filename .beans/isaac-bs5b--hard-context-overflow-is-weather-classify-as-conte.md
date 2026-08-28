---
# isaac-bs5b
title: 'Hard context overflow is weather: classify as context-exhausted so hail defers, never 5× stuffing'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-08-28T16:04:05Z
updated_at: 2026-08-28T17:12:52Z
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

## Decision (2026-08-28, Micah)

Approved scenario plan (4) and the four scenarios as written.

## Scenarios (approved, @wip on main)

1. `isaac-agent` `features/llm/provider_walls.feature:99` — a provider 400 for prompt length classifies as context-exhausted
2. `isaac-agent` `features/llm/provider_walls.feature:120` — a generic provider 400 stays an api-error
3. `isaac-agent` `features/llm/provider_walls.feature:142` — overflow weather does not leave the rejected user turn on the transcript
4. `isaac-hail` `features/context_window_guard.feature:83` — a provider 400 for prompt length defers the hail without burning attempts

Hail production code should not change. S4 is the stack proof: grover `http-error` (not pre-classified `unavailable`), compaction enabled so isaac-dark's pre-request guard does not fire.

## Acceptance

- [ ] `cd isaac-agent && bb features features/llm/provider_walls.feature:99`
- [ ] `cd isaac-agent && bb features features/llm/provider_walls.feature:120`
- [ ] `cd isaac-agent && bb features features/llm/provider_walls.feature:142`
- [ ] `cd isaac-hail && bb features features/context_window_guard.feature:83` (use `:dev-local` against the agent bean branch)
- [ ] `@wip` removed from the four scenarios
- [ ] Hail's isaac-agent pin bumped to the SHA that contains the classify+rollback fix (CI does not use `:dev-local`)

DoD: `@wip` gone and the four commands pass. Specs for `isaac.drive.provider-wall` as needed.

## Notes

- Grover `http-error` already carries `:status` / `:message`; real `isaac.llm.http` 400s put the text on `:body :error :message`. `response-message` already reads both.
- `isaac service install` dropping plist env is a different bean (talk after this).


## Verify fail (attempt 1, 2026-08-28): required hail `bb features` stack-proof command is still red under the native path

Evidence:
- Verified implementation exists on `isaac-agent` `origin/bean/isaac-bs5b` at `5701875` and `isaac-hail` `origin/bean/isaac-bs5b` at `9e802ed`.
- Agent-side acceptance is green on the bean branch:
  - `bb features features/llm/provider_walls.feature:99` → `1 examples, 0 failures, 4 assertions`
  - `bb features features/llm/provider_walls.feature:120` → `1 examples, 0 failures, 2 assertions`
  - `bb features features/llm/provider_walls.feature:142` → `1 examples, 0 failures, 4 assertions`
  - `bb spec spec/isaac/drive/provider_wall_spec.clj spec/isaac/session/store/memory_spec.clj` → `23 examples, 0 failures, 49 assertions`
- The hail bean branch removed `@wip` from `features/context_window_guard.feature:83` and bumped its isaac-agent pin to `57018753a4e5498b2b6e74f380b51ccba9e9efb3` in both `deps.edn` and `bb.edn`.
- But the required acceptance command is still red when run as written against paired bean worktrees (`isaac-hail` bean branch with local sibling `isaac-agent` bean branch):
  - `bb features features/context_window_guard.feature:83`
  - Fails during SCI analysis with `Unable to resolve symbol: fs/size` from `isaac-agent/src/isaac/session/store/impl_common.clj`.
- For contrast only, the JVM fallback stack proof is green:
  - `clojure -M:features features/context_window_guard.feature:83` → `1 examples, 0 failures, 4 assertions`

Conclusion: bean DoD is not yet met because the named acceptance command still fails. Do not re-hand off until `cd isaac-hail && bb features features/context_window_guard.feature:83` is green, or planner amends the acceptance.
