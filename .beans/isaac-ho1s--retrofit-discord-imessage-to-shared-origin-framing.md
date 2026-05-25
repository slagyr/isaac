---
# isaac-ho1s
title: Retrofit Discord + iMessage to shared origin framing
status: draft
type: task
priority: normal
created_at: 2026-05-25T18:16:21Z
updated_at: 2026-05-25T23:45:13Z
parent: isaac-ugx7
blocked_by:
    - isaac-ffhl
---

## Motivation

Discord and iMessage hand-roll framing — `build-trusted-block` passed as
`:soul-prepend`, which busts the cache and lies in the soul. Retrofit both to
the shared model (A's guard + nonce, B's charge `:guidance` + current-turn
injection): stamp `:guidance` on the charge, keep `:origin`, and drop the
hand-rolled block. The "reduce code" payoff.

## Scope (cross-repo)

- `../isaac-discord`: `DiscordComm` stamps `:guidance` on its charge (text moved
  from `build-trusted-block` / `build-user-prefix`); keep `:origin {:kind
  :discord …}`; remove the `:soul-prepend` framing.
- `../isaac-imessage`: stamp `:guidance` (brevity/bubbles text moved from
  `build-trusted-block`); keep `:origin {:kind :imessage …}`; remove
  `:soul-prepend`.
- Both depend on isaac via `:local/root "../isaac"`, so they pick up the charge
  `:guidance` field + the framing helper directly.

## Scenarios

- Update each repo's existing prompt/framing specs to the unified shape
  (guidance now in the current-turn nonce-tagged block, not `:soul-prepend`) and
  keep them green.
- Assert Discord/iMessage turns stamp their guidance into the current user turn.

## Notes

- Could split one-per-repo for parallel workers.

## Relationship

- Parent: isaac-ugx7. **Blocked by isaac-ffhl (B).** Type: `task`. Cross-repo.
