---
# isaac-61ob
title: "Discord: split long responses at the 2000-char cap"
status: completed
type: feature
priority: low
created_at: 2026-04-20T23:07:42Z
updated_at: 2026-04-21T19:44:55Z
---

## Description

Milestone 6 of the Discord channel adapter epic. When the crew reply exceeds Discord 2000-char limit, split into multiple messages sent in order. Prefer natural boundaries (newlines) when possible; hard-split inside a chunk only if a single line exceeds the cap. Fenced code blocks span splits cleanly: close the block at the end of one message, reopen at the start of the next. Depends on the reply bead (REST send).

## Acceptance Criteria

1. Implement splitting with comms.discord.message-cap config (default 2000).
2. Newline-boundary splitting for multi-line content.
3. Hard-split fallback for a single line exceeding the cap.
4. Shared 'an outbound HTTP request to URL matches:' step-def (same as isaac-gxro).
5. Remove @wip from both scenarios in features/comm/discord/splitting.feature.
6. bb features features/comm/discord/splitting.feature passes.
7. bb features passes overall.
8. bb spec passes.

## Design

Implementation notes:
- Message cap via config: comms.discord.message-cap (default 2000, integer).
- Split algorithm:
  1. If content fits under cap, send as one POST.
  2. Else split at newline boundaries into chunks that each fit under cap.
  3. For any chunk that is itself over cap (a single line longer than cap), hard-split at the cap boundary (no character class awareness — just byte/char count).
- POST each chunk sequentially in order; do not interleave.
- Depends on isaac-lkiy (REST reply) — splitting is a refinement of the single-POST path. The chunker wraps what used to be a single POST.
- Code-block handling (fenced triple-backtick spanning split) can be a follow-up; not in this bead's scope.

Reuses the 'an outbound HTTP request to URL matches:' step-def introduced in isaac-gxro. Both beads land the step; either can be first and the other is a trivial consumer.

