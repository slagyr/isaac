---
# isaac-6488
title: 'isaac-6yg0 field check: toad ACP opens a marvin episode on zanebot'
status: draft
type: task
priority: normal
created_at: 2026-08-29T14:50:28Z
updated_at: 2026-08-29T14:50:28Z
---

Split from isaac-6yg0. Product + pin-bump + episodes.feature are green on `isaac-acp` `origin/bean/isaac-6yg0` @ `02d00e0` (4/0 episodes, 64/0 acp features, 70/0 spec). Verify cannot complete 6yg0 because acceptance item 3 required a live zanebot deploy check recorded in the bean.

## Field check (after 6yg0 is deployed on zanebot)

1. `toad acp "zane-isaac acp --crew marvin --create always"`
2. One prompt on that ACP session
3. `isaac episodes list --crew marvin` shows an **open** episode whose `:thread` is the ACP session id
4. Recall block present when the query matches the corpus
5. Record evidence (episode id, ACP session id, log `:episodes/opened :origin :acp`) on this bean

## Notes

- Do not reopen ACP-through-the-bridge product work unless the field check fails.
- Blocked by 6yg0 landing + deploy (modules pin / service already running new acp).
- Draft until scenarios or a one-line runnable check is promoted.
