---
# isaac-2bni
title: 'ACP cancellation.feature flake: session/cancel during a turn sometimes lands after end_turn'
status: draft
type: bug
tags:
    - acp
    - flake
created_at: 2026-09-03T16:40:56Z
updated_at: 2026-09-03T16:40:56Z
---

Observed 2026-09-03 on isaac-acp main (3feb970 → 0cd2677) during the episodes train gate: features/comm/acp/cancellation.feature 'session/cancel during a turn stops processing' failed 2 of 5 runs with result.stopReason end_turn instead of cancelled; the other 3 runs and the full 64-scenario suite passed. A timing race between the cancel arriving and the fake turn finishing (compare isaac-wa06 / isaac-q9b0 / isaac-zcb9 cancel_aborts_work flakes). Replace the sleep-shaped wait with explicit signaling (isaac-se23 pattern) so the cancel is guaranteed to land mid-turn. Not a blocker; recorded so the gate stays trustworthy.
