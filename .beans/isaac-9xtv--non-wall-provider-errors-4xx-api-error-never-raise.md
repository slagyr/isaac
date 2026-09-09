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

Repo: **isaac-agent** (`src/isaac/attention.clj`, `src/isaac/drive/turn.clj`
around the `:chat/response-failed` log); hail already covers its own paths.

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

## Non-goals

Retry/backoff policy for 400s; changing what hail does with walls or auth.

## Acceptance

- Scenario: hook turn gets a 400 from the provider → one attention post
  naming provider, model, session and the detail.
- Scenario: three hook turns on the same provider fail within a minute →
  one post.
- Scenario: episode seal fails 5 times → one post.
- Scenario: two different providers fail → two posts.
- `bb spec` and `bb features` green.
