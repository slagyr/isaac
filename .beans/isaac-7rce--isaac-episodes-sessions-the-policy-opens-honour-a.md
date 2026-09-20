---
# isaac-7rce
title: 'isaac-episodes: sessions the policy opens honour a requested name and otherwise get the agent''s adjective-noun names'
status: in-progress
type: feature
priority: high
tags:
    - episodes
    - agent
created_at: 2026-09-20T00:06:00Z
updated_at: 2026-09-20T04:52:09Z
blocked_by:
    - isaac-8s6s
---

Micah 2026-09-19: episode sessions on yopp are named by episode timestamp (20260918025608859) while chronicle sessions get the agent's star-themed adjective-noun names (clever-dove). Both should behave the same: when a comm or CLI opens a session with an explicit name/identifier (e.g. gchat's canonical per-space session, --session foo), the episodes policy keeps that as the session id; when none is given it asks the agent's naming strategy (isaac.session.store.spi/make-naming-strategy — adjective-noun by default, sequential when configured) instead of minting a timestamp. Episode ids stay timestamps; it is the SESSION id that changes. Scenarios (episodes features): open with a name → session id is the name; open without → adjective-noun; recall/lineage unaffected.



**Re-scoped 2026-09-19 (Micah):** the AGENT owns session naming. Episode ids stay timestamps; the backing session is named by the agent's naming strategy before any policy's open-session! is called, and a policy takes the identifier it is given. Today isaac-episodes/lifecycle.clj falls back to the episode id when no :session-id is supplied — that fallback goes, and the agent's api/open-session! (and every path that opens through a policy: comms, CLI, hail) mints a name when the caller has none. Work is mostly in isaac-agent; episodes loses its fallback.
