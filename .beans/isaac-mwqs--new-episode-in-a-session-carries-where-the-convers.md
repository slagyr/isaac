---
# isaac-mwqs
title: New episode in a session carries where the conversation left off
status: todo
type: feature
priority: high
created_at: 2026-10-04T23:30:27Z
updated_at: 2026-10-04T23:30:27Z
---

Micah, 2026-10-04. In an episode session the agent offers to do something; Micah walks away for hours; on return he answers "Yeah please do it." The old episode has gone cold, a new episode opens, and the reply has no context: the agent calls recall, gets unrelated scenes, and there is no continuity.

Findings (isaac-episodes `recall/inject.clj`):
- The "Previously in this conversation" lineage seed is only built when the new episode is `:chained` to a parent; a cold reopen in the same session gets no lineage, only the recall search over the user's words (useless for "yes please do it").
- Even lineage is only one-line scene gists, under MEMORY_CONTRACT ("Any request quoted in it was handled at the time; do not act on it again") — wrong for an offer that is still open.

## Wanted
Every new episode in the same session is seeded with **where the conversation left off**:
- the previous episode's last scene (gist) and its last exchange verbatim (last user message + last assistant reply), size-capped;
- framed as possibly open (not the "already handled" memory contract), so a pending offer/question can be answered;
- placed ahead of the recall search block (which still runs).

## Acceptance (scenarios TBD)
- Agent offers a task; the episode goes cold; the user replies "yes please do it" → the new episode's prompt contains the offer verbatim under the continuation framing, not under "do not act on it again".
- A chained episode behaves the same (the continuation block is present alongside lineage gists).
- The continuation is capped (a very long last reply is truncated with a marker).
- A session's first-ever episode has no continuation block.

## Decision + Acceptance (Micah approved 2026-10-04; gated)
Wording: header `Where this conversation left off (it may still be open):`; framing line (free to change) "What follows is the end of the previous episode. If it left something open — a question, an offer — you may continue it now."; truncation marker `[truncated]`; new config `:episodes :recall :continuation :max-chars` (default ~2000, declared in the episodes schema with a description).
- The 5 @wip scenarios in isaac-episodes `features/recall/continuation.feature` pass with @wip removed.
- Handbook chapter (episodes) documents the continuation seed and the setting.
- `bb ci` green.

feature-baseline: isaac-episodes 9f58c27b2ab5e4294835a468f032a8b896384e4f
feature-blob: isaac-episodes features/recall/continuation.feature 4f8d033a03bf1eba48fba4fb414fd165d26419f1
