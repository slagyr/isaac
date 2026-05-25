---
# isaac-uysx
title: 'Shared comm-origin framing: extract the origin+autonomy turn preamble'
status: draft
type: feature
created_at: 2026-05-25T01:01:23Z
updated_at: 2026-05-25T01:01:23Z
parent: isaac-ugx7
---

Deferred from isaac-wte9 (hail delivery worker).

The hail delivery worker opens each turn with an **origin+autonomy system preamble**: this turn came from hail <id> (addressing, payload), it runs unattended, and the user may not see the reply or be available for questions (so don't block on clarification). Discord/iMessage-style adapters do the analogous origin-framing for their channels.

This bean would extract a **shared comm-origin framing helper** so hail, cron, and future channels build their turn preamble consistently instead of each reinventing it.

## Open questions (refine before work)
- What fields the framing takes (origin kind, sender, attended/unattended, reply visibility).
- Where it lives (comm layer? a shared turn-context helper?).
- How a channel opts in / overrides the default wording.

## Relationship
- Parent: isaac-ugx7 (Hail epic).
- Generalizes the hail-specific preamble built in isaac-wte9 (no shared helper exists as of this planning).
