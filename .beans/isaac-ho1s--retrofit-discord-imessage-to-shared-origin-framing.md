---
# isaac-ho1s
title: Retrofit Discord + iMessage to shared origin framing
status: draft
type: task
priority: normal
created_at: 2026-05-25T18:16:21Z
updated_at: 2026-05-25T18:16:21Z
parent: isaac-ugx7
blocked_by:
    - isaac-ffhl
---

## Motivation

Discord and iMessage already hand-roll origin framing — each builds a
`build-trusted-block` and passes it as `:soul-prepend`, which merges into the
cached system block (busting the cache, lying in the soul). Retrofit both to the
shared model from isaac-uysx (A) + isaac-uysx-B: set `:origin`, let the
universal guard + per-turn nonce-tagged block do the work, and drop the
hand-rolled block. This is the "reduce code" payoff.

## Scope (cross-repo)

- `../isaac-discord`: remove `build-trusted-block` / `:soul-prepend` framing;
  set `:origin {:kind :discord …}`; rely on the shared per-turn origin block.
  Move its attended/conversational guidance into B's kind→guidance table.
- `../isaac-imessage`: same — remove `build-trusted-block`; set
  `:origin {:kind :imessage …}`; its "keep brief / bubbles" guidance moves into
  B's table.
- Both depend on isaac as `:local/root "../isaac"`, so they import the shared
  helper directly.

## Scenarios

- Update each repo's existing prompt/framing specs to the unified format
  (the trusted-block text now comes from the shared helper, in the current
  user turn, nonce-tagged) and keep them green.
- Assert Discord/iMessage turns carry the attended (conversational) guidance
  variant, not the autonomy variant.

## Notes

- Could split one-per-repo for parallel workers; kept as one bean with a
  per-repo checklist for now.
- Attended-vs-unattended guidance is the key behavioral difference these
  channels exercise (vs hail/cron's unattended framing in B).

## Relationship

- Parent: isaac-ugx7.
- **Blocked by isaac-uysx-B** (the shared helper + per-turn block must exist).
- Type: `task`. Cross-repo (`../isaac-discord`, `../isaac-imessage`).
