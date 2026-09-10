---
# isaac-0yoc
title: 'isaac-acp: session/new and session/load stop branching on the crew''s mode — the store answers'
status: in-progress
type: task
priority: normal
created_at: 2026-09-09T16:35:02Z
updated_at: 2026-09-10T09:18:20Z
blocked_by:
    - isaac-mmod
---

Repo: isaac-acp. Blocked by isaac-mmod. `server.clj` `session-new-handler` (fresh-mint for episode crews vs create-with-resolved-behavior!) collapses to `default-session`/`open-session!` on the crew's store; `attach-session-result!`/`replay-open-episode!` (find-open-on-thread + active-transcript of the backing session) collapses to `active-transcript` of the session id. Remove the `isaac.episodes.lifecycle` / `isaac.episodes.store` requires. Acceptance: `features/comm/acp/episodes.feature` and `session.feature` unchanged and green (the 6yg0 fresh-thread scenario is the proof); `grep -rn 'isaac\.episodes' src` empty.
