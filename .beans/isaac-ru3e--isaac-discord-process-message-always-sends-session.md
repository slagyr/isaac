---
# isaac-ru3e
title: 'isaac-discord: process-message! always sends :session-key; drop episode/chronicle request-shape branch'
status: scrapped
type: task
priority: high
tags:
    - discord
created_at: 2026-09-10T06:18:04Z
updated_at: 2026-09-10T06:27:56Z
parent: isaac-mmod
blocked_by:
    - isaac-mmod
---

Repo: isaac-discord. Blocked by **isaac-mmod**. Child of the session-policy train.

Micah (mmod recut): Discord's episode/chronicle request-shape branch collapses to a **plain session id**. The typing heartbeat is store-agnostic and stays that way. Session id never changes; no `:conversation` on the charge.

## Why this exists

isaac-mmod deleted the agent conversation router. Bridge dispatch now requires `:session-key`. On `isaac-agent` `bean/isaac-mmod` @ `1fa612e`, `features/comm/discord/episodes.feature` is red even after the verifier-local plant re-key `:conversation` → `:session-policy`:

- `src/isaac/comm/discord.clj` `process-message!` still:
  - `episode?` → `:conversation {:kind :thread :id session-name}` and **omits `:session-key`**
  - chronicle → `:session-key session-name`
- Without `:session-key` the bridge never opens a session, so the episodes policy never opens a container.
- Chronicle scenario stays green. Episodes scenarios (first message opens an episode; warm second message) fail: crew has 0 episodes.

This is Discord production request-shape, not an agent bug. Do **not** recut Discord inside isaac-agent.

## Required

1. `process-message!` always sets `:session-key` to the Discord session name (channel/thread identity). Never put `:conversation` on the charge.
2. Drop the episode? vs chronicle request-shape branch. `lifecycle/episodes-crew?` is not needed to shape the dispatch; the crew's `:session-policy` is the agent's problem.
3. Plant lines in `features/comm/discord/episodes.feature` re-key `:conversation | episodes` → `:session-policy | episodes` (the config rename from mmod). Assertions recut only as needed so they prove: an episodes crew opens a container on first message; a warm second message appends to the same session id; session id never changes.
4. Typing heartbeat (qomx) remains store-agnostic — runs regardless of `:session-policy`.

## Acceptance

    cd isaac-discord
    # deps.edn / bb.edn agent pin = the SHA isaac-mmod releases
    bb jvm-features features/comm/discord/episodes.feature
    bb jvm-features features/comm/discord/typing.feature
    bb spec spec/isaac/comm/discord_spec.clj

0 failures. `grep -n ':conversation' src` empty (or only comments). Do **not** require full `bb features` wrapper exit 0 (60s timeout is known). Do not reopen mmod product.

Draft until human review. Blocked by mmod landing.

## Reasons for Scrapping

Duplicate of isaac-6ele (created 2026-09-09 under Micah's ruling that the Discord request shape collapses to a plain session id). Its specifics (session-key always, plant re-key, acceptance commands, agent pin) were folded into 6ele on 2026-09-10.
