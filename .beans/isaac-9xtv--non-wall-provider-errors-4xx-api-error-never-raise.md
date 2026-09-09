---
# isaac-9xtv
title: Non-wall provider errors (4xx api-error) never raise attention; hooks, cron and episode seals fail silently
status: todo
type: bug
priority: high
tags:
    - agent
    - attention
    - provider-weather
created_at: 2026-09-09T14:36:34Z
updated_at: 2026-09-09T14:36:34Z
---

Repo: **isaac-agent**. One seam: `isaac.drive.dispatch/log-dispatch-result`
(every provider call — user turns, tool loops, episode seals — passes through
it). Notifier lives in `isaac.attention`. Hail already covers its own paths.

## Problem

A provider that answers every request with a non-wall 4xx fails silently.
Attention today fires on exactly three paths: an uncaught throwable in a turn
(`maybe-notify-turn-failed!`), conversation-blocked, and hail deferral
(dead-letter, auth wall). A handled `:api-error` (400 from the provider) ends
the turn cleanly with an error entry and `:chat/response-failed` at error
level, and nobody is told. Turns that never pass through hail delivery —
hooks, cron, episode seals, comm-originated turns — have no attention path
at all.

Observed on zanebot 2026-09-09: the chatgpt provider rejected every model
(`The '<model>' model is not supported when using Codex with a ChatGPT
account.`, HTTP 400). Consequences, all unreported:

- the daily 07:00Z heartbeat cron failed every morning from 09-06 to 09-09;
- every health hook (sleep, spo2, respiratory-rate, heartrate, hrv, activity,
  location) failed on arrival;
- an episode seal retried every 30 s for 16 minutes (`:episodes/seal-failed`
  `:reason :provider-error`) until the model was hand-repointed.

Micah found out by reading the log. The 429 wall and 401/403 auth cases are
covered because hail defers them and posts attention; a 400 (bad model,
plan lost Codex access, malformed request) is the gap.

## Expected

- A provider error that is not a wall and not auth (4xx `:api-error` with a
  provider `:detail`/message) on ANY turn origin posts attention with the
  provider, model, session and the provider's message.
- Throttled **per provider** (not per session): first failure posts, then at
  most one post per hour while it keeps failing, with a count of suppressed
  failures. A recovery post when the provider next succeeds is nice to have.
- Episode seal `:provider-error` and hook/cron turn failures go through the
  same notifier; the seal retry loop must not post thirty times.
- Existing turn-failed and conversation-blocked posts unchanged.

## Decisions (2026-09-09, Micah)

1. **One place.** Broken-provider identification lives in the drive, in
   `dispatch/log-dispatch-result`, not at call sites. Episodes, comms, hooks,
   cron and hail are not touched.
2. **One classifier.** `provider-wall/classify` decides weather vs broken.
   The overflow-400 check (`prompt-too-long?`, today in turn.clj) moves into
   provider-wall so dispatch and turn share it; dispatch posts attention only
   for results classify leaves as `:api-error` / `:llm-error`.
3. **Throttle** is per provider, one hour, in memory (same shape as the
   per-session throttle in `isaac.attention`). Hourly repost carries the
   suppressed count. A restart may cost one duplicate post; accepted.
4. Recovery post is out of scope.

## Non-goals

Retry/backoff policy for 400s; changing what hail does with walls or auth;
a recovery post when the provider comes back.

## Acceptance

Scenarios: `isaac-agent/features/llm/provider_attention.feature` (five, @wip,
commit ea60ec0). One new step — `the newest file in {path} EDN contains:` —
goes in isaac-agent's own steps (not foundation spec-support; no pin bump).

```
cd isaac-agent
bb features features/llm/provider_attention.feature
bb features features/llm/provider_walls.feature
bb features features/session/context_window_guard.feature
bb features features/session/compaction_overflow.feature
bb spec spec/isaac/drive spec/isaac/attention_spec.clj
bb ci
```

All five pass with @wip removed; the three neighbouring features stay green
after `prompt-too-long?` moves into provider-wall. bb ci green.
