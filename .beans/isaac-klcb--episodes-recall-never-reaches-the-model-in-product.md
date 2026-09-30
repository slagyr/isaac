---
# isaac-klcb
title: 'Episodes recall never reaches the model in production: the held block lives on the agent session record, which the sidecar store strips'
status: todo
type: bug
priority: critical
created_at: 2026-09-30T05:05:47Z
updated_at: 2026-09-30T05:06:12Z
---

Likely repo: **isaac-episodes**. Critical: live on zanebot since the 2026-09-30 02:38Z deploy (episodes 8ef1955, which carries isaac-jvwr).

## Why

isaac-jvwr holds the cold-open recall block on the **agent's session
record** (`:pending-recall` via `update-session!`) and consumes it on the
next user append. Agent's `Session` schema (`isaac.session.schema/Session`)
has no such key, and the production sidecar store conforms every write
(`sidecar/update-sidecar-entry!` → `conform-session!`), so the block is
stripped the moment it is written. Result: no episodes crew in production
receives recall at a cold open, full-context crews (Zane) included. The
jvwr scenarios passed because the feature harness uses the in-memory
session store, which does not conform.

Field, 2026-09-30 02:43Z, Mixmaster (isaac-work-4) on isaac-3ljt:
`:episodes/recalled` logged 10 lineage scenes, yet the stored opening user
message holds only the hail prompt and no `:pending-recall` exists anywhere.

## Design

- Episodes keeps the held block in state it owns, not on the agent's
  session record: the open episode's record (`episode.edn`, via the
  episodes store) is the natural home. `append-message!` reads it from the
  open episode, prefixes the next user message, and clears it there.
- No agent schema change; agent stays ignorant of episodes.
- Keep the jvwr message shape (recall is a prefix of the opening user
  message) so isaac-3ljt's seal stripping still applies.

## Acceptance

- isaac-episodes `features/episodes/live.feature` — "recall reaches the opening prompt on the file-backed session store"
- The rest of `features/episodes/live.feature` stays green (jvwr, 3ljt, recall-at-open).

feature-baseline: isaac-episodes f5add9063ce34b1a344888ebce43540071cc35b2
feature-blob: isaac-episodes features/episodes/live.feature 4f9e5e2a7a7b3fd78ab49c8d0fb07f24228e54a7 407
