---
# isaac-ffhl
title: 'Comm-origin framing: per-turn :origin block in the current message (hail + cron)'
status: draft
type: task
priority: normal
created_at: 2026-05-25T18:16:21Z
updated_at: 2026-05-25T18:16:21Z
parent: isaac-ugx7
blocked_by:
    - isaac-uysx
---

## Motivation

Tell the model each turn's **origin** and **audience expectations** without
busting the prompt cache or lying in the soul. Builds on the guard + nonce from
**isaac-uysx (A)**.

A crew session is **multi-origin** — it takes CLI, hail, and cron turns
interleaved — so origin framing must NOT live in the (stable, cached) system
prompt. It rides with the turn it describes.

## Model

`:origin → {guidance, metadata}` helper. Inject the result as a **nonce-tagged
block into the current user turn** (the live message), **never persisted to the
transcript**:

- **guidance**, keyed on origin kind / attended-ness — unattended (hail, cron):
  "autonomous run; the user may not see your reply and may be unavailable, so
  don't block on questions"; attended (future chat channels): conversational
  variant.
- **metadata** (kind, ids), nonce-tagged so A's guard treats it as trusted.

Current-turn-only means: one guidance block per turn (no accumulation /
confusion), the cached history stays pure user/assistant, and only the small
origin block is uncached. **Do not place a cache breakpoint on the
origin-bearing message** (its bytes differ live `[origin+text]` vs historical
`[text]`, so a breakpoint there buys a wasted cache-write).

## Scope

- `:origin → {guidance, metadata}` helper (the kind→guidance table lives here).
- Inject the nonce-tagged block into the current user message at request-build;
  never store it.
- New step: extend `the prompt "<input>" on session "<key>" matches:` to thread
  an `:origin` so framing can be asserted on the composed prompt.
- Wire **hail** (already sets `:origin`, via wte9) and **cron** (gains framing).

## Scenarios (to draft; via the origin-threaded `the prompt … matches:`)

- Unattended origin (hail/cron) → the current user message carries a
  nonce-tagged block with autonomy guidance + metadata; origin is **not** in the
  system block; no cache breakpoint on that message.
- The origin block is current-turn-only — a second turn shows exactly one
  guidance block (the new one), not two.
- cron turn gains the unattended framing.

## Relationship

- Parent: isaac-ugx7.
- **Blocked by isaac-uysx (A)** — consumes the nonce/guard trust contract.
- **Blocks isaac-uysx-C** (Discord/iMessage retrofit).
- Type: `task`.
