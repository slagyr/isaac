---
# isaac-ffhl
title: 'Comm-origin framing: per-turn :origin block in the current message (hail + cron)'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-05-25T18:16:21Z
updated_at: 2026-05-26T00:05:18Z
parent: isaac-ugx7
blocked_by:
    - isaac-uysx
---

## Motivation

Per-turn origin framing: tell the model each turn's origin/audience without
busting the prompt cache or lying in the soul. Builds on the guard + nonce from
isaac-uysx (A). A crew session is multi-origin (CLI + hail + cron interleaved),
so framing rides with the turn, never in the (cached) system prompt.

## Model

Guidance is **charge data**, set by the dispatcher — symmetric with `:origin`.
No polymorphism: guidance is known at dispatch time, so whoever builds the
charge stamps it. No `TurnFraming` protocol, no `HailComm`/`CronComm` —
NullComm stays a pure null object.

- **Charge gains `:guidance`** (string) alongside `:origin` (metadata map).
- **Framing helper** at request-build: if `:guidance`/`:origin` present, render
  `:origin` generically (tagged), combine with `:guidance`, tag with the
  **session nonce** (A), and inject as a block into the **current user turn**,
  **never persisted**.
- **Dispatchers stamp `:guidance`:** hail worker and cron service set their
  guidance on the charge (keep `null-comm` for output). Each owns its guidance
  string (a private const/fn).

## Caching

Current-turn-only → one guidance block per turn (no accumulation), clean cached
history; the origin-bearing message is **uncached** (no breakpoint on it — the
breakpoint stays on the origin-free penultimate message).

## Scope

- Add `:guidance` to the charge; framing helper (generic `:origin` render +
  guidance + nonce-tag + current-turn injection, never persisted).
- Hail worker + cron service stamp `:guidance`.
- New test step threading `:origin` + `:guidance` into the synthetic prompt
  build: `the prompt "<input>" on session "<key>" with origin {…} and guidance "<text>" matches:`.

## Scenarios (to draft)

- A turn with guidance+origin → current user message carries a nonce-tagged
  block (guidance + rendered metadata); origin not in system; message uncached.
- Multi-turn → block is current-turn-only; history clean; breakpoint on the
  origin-free historical message.
- Hail worker / cron service stamp the expected guidance (dispatcher checks).

## Relationship

- Parent: isaac-ugx7. **Blocked by isaac-uysx (A).** **Blocks isaac-ho1s (C).**
  Type: `task`.


## Feature file

`features/session/origin_framing.feature` — 2 `@wip` scenarios (helper wraps guidance + origin into a current-turn nonce-tagged block, uncached, not in system; framing is current-turn-only so history stays clean + cacheable). Run:

```
bb features features/session/origin_framing.feature
```

**Definition of done:** remove `@wip` and green. Adds: `:guidance` on the charge; the framing helper (generic `:origin` render + guidance + nonce-tag + current-turn injection, never persisted); the `… with origin {…} and guidance "…" matches:` test step. Plus the **dispatcher requirement** (not in this file): the hail worker and cron service must stamp `:guidance` on their charges — verified by a lighter check where each worker runs, not a synthetic prompt assertion.
