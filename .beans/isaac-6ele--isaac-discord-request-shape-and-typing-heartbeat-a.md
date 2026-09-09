---
# isaac-6ele
title: 'isaac-discord: request shape and typing heartbeat are store-agnostic'
status: todo
type: task
priority: normal
created_at: 2026-09-09T16:35:02Z
updated_at: 2026-09-09T16:35:02Z
blocked_by:
    - isaac-mmod
---

Repo: isaac-discord. Blocked by isaac-mmod. `discord.clj` builds `:conversation {:kind :thread …}` for episode crews and `:session-key` otherwise (lines ~375–398) — collapses to `:session-key` always; the typing heartbeat currently starts only for episode sessions (first cycle) — per Micah (2026-09-09) it runs regardless of the session store. Remove the `isaac.episodes.lifecycle` require. Acceptance: Discord features (`routing.feature`, `scuttlebutt.feature`, typing scenarios) green; the typing scenario's crew no longer needs to be an episodes crew (rewrite that Given to a chronicle crew, @wip first); `grep -rn 'isaac\.episodes' src` empty.
