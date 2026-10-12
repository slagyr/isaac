---
# isaac-mcfc
title: 'isaac-hail: drop the legacy origin.from now that turns carry :from'
status: draft
type: task
created_at: 2026-10-12T17:49:33Z
updated_at: 2026-10-12T17:49:33Z
parent: isaac-zt1x
blocked_by:
    - isaac-rrk6
---

Follow-up to isaac-rrk6 (Micah, 2026-10-12: keep it separate, but the duplication should not stay).

Hail writes `:origin {:from …}` on each turn as `:crew/<id>`, `:http` or `:cli`, and prints a "From crew:" line in the metadata preamble. Once isaac-rrk6 lands, the turn's top-level `:from` says the same thing in the shared shape.

Rough intent, to refine before this is ready:

- Remove `:from` from the hail origin; readers use the turn's `:from`.
- Decide what the preamble line becomes (it says "From crew: :http" for HTTP hails today, which is wrong).
- Many frozen scenarios assert `origin.from` (send, send-addressing, http, crew-tool). They need new gate lines, not in-place edits.
- Check other readers of `origin.from` across repos first (`isaac hail` CLI output, handbook, band prompts, zanebot skills).
