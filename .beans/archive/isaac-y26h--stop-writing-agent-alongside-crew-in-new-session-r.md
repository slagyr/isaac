---
# isaac-y26h
title: "Stop writing :agent alongside :crew in new session records"
status: completed
type: task
priority: low
created_at: 2026-04-23T01:33:00Z
updated_at: 2026-04-23T20:23:46Z
---

## Description

New session records still include both :crew and :agent keys even though the codebase has settled on :crew. This keeps the 'agent' field alive in fresh data.

Write sites to update:
- src/isaac/cli/prompt.clj:92 — storage/create-session! call writes {:crew agent-id :agent agent-id}
- src/isaac/comm/discord.clj:59 — same dual-key write
- Any session-storage write path that mirrors :crew into :agent

Keep only :crew on write. Old sessions are acceptable casualties — the read-fallback is being removed in a follow-up bead.

Acceptance:
1. grep for ':agent' in src/ returns no write-side occurrences (reads are handled in the follow-up bead)
2. bb features and bb spec pass
3. Inspecting a freshly created session .jsonl entry shows no :agent key

## Notes

Completed with bb spec green, bb features green, and a freshly created session index entry verified without an :agent key.

