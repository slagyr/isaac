---
# isaac-0yoc
title: 'isaac-acp: session/new and session/load stop branching on the crew''s mode — the store answers'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-09-09T16:35:02Z
updated_at: 2026-09-10T10:46:54Z
blocked_by:
    - isaac-mmod
---

Repo: isaac-acp. Blocked by isaac-mmod. `server.clj` `session-new-handler` (fresh-mint for episode crews vs create-with-resolved-behavior!) collapses to `default-session`/`open-session!` on the crew's store; `attach-session-result!`/`replay-open-episode!` (find-open-on-thread + active-transcript of the backing session) collapses to `active-transcript` of the session id. Remove the `isaac.episodes.lifecycle` / `isaac.episodes.store` requires. Acceptance: `features/comm/acp/episodes.feature` and `session.feature` unchanged and green (the 6yg0 fresh-thread scenario is the proof); `grep -rn 'isaac\.episodes' src` empty.


## Handoff (scrapper@isaac-work-1)

branch: bean/isaac-0yoc @ 1453a8c6e34f731fd160ec23b5613a4c8709cd4f (base origin/main@79cc310d75555174dc726eaafc30524414ffbf6a)

ACP session/new and session/load no longer branch on isaac.episodes.lifecycle/store. Callers use SessionPolicy (for-crew, default-session, open-session!, active-transcript). grep src isaac.episodes empty.

Promote crew :conversation → :session-policy after normalize so unchanged episodes.feature fixtures still select the episodes policy.

Local: ISAAC_GIT=1 bb jvm-spec ACP cli+server 58 examples / 1 failure (pre-existing cancel tool_call_update, not this bean). jvm-features episodes+session 12 examples / 2 failures: :episodes/opened log still uses :session-id not :thread; warm sessions match episode-id regex vs stable session id reef-chat. Features left unchanged per bean.

Pin agent 837b6d4 (mmod 0.1.58).
