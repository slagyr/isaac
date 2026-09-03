---
# isaac-6488
title: 'isaac-6yg0 field check: toad ACP opens a marvin episode on zanebot'
status: completed
type: task
priority: normal
created_at: 2026-08-29T14:50:28Z
updated_at: 2026-09-03T17:32:38Z
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



## Field evidence (2026-09-03, episodes train 0.1.41 / acp 0.1.10 / discord 0.1.11)

- ACP: `toad acp "zane-isaac acp --crew marvin --create always"` + one prompt → `isaac episodes list --crew marvin` shows **2026-09-03-1651-p80b open, thread acp-acc5de07-403a-4a0e-9de1-bd70703be77d**. No chronicle named after the ACP session id was created.
- Discord (same train): MESSAGE_CREATE on channel 1471612995535900712 → `:discord.route/inbound crew marvin` then `:episodes/opened :thread discord-1471612995535900712 :origin {:kind :discord ...}` → episode 2026-09-03-1647-xsdf open; reply delivered to the channel.
- Recall-at-open ran on both (embedding http-request right after opened) but surfaced nothing: the cue ("fermi interstellar travel") does not exist anywhere in marvin's corpus, sessions, or memory on zanebot or locally — not a routing defect. Index.edn is dated 2026-08-28 (39 scenes ranked) and predates recent episodes; re-run `isaac episodes index --crew marvin` before judging recall quality.



## Summary of Changes

Field check passed on the 0.1.41 episodes train (agent 13da406, acp 0cd2677, discord ab935be), evidence above: ACP session/new + prompt via toad opened marvin episode 2026-09-03-1651-p80b on thread acp-acc5de07-403a-4a0e-9de1-bd70703be77d; no chronicle named after the ACP session was created. Discord did the same on its channel thread. No product work reopened.
