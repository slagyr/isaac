---
# isaac-8uno
title: episodes list prints '-' in the thread column after the layout migration — should show :session-id
status: completed
type: bug
priority: low
created_at: 2026-09-11T04:43:30Z
updated_at: 2026-10-06T23:01:40Z
parent: isaac-b6w0
---

Repo: isaac-agent (src/isaac/episodes/cli.clj list). After migrate-layout, episode.edn carries :session-id and no :thread (move-episode! dissocs :thread); `isaac episodes list --crew marvin` now prints '-' where it used to print the thread id (e.g. 2026-09-10-1524-qc65  closed  -  51 scenes). Print :session-id (falling back to :thread for unmigrated records). Scenario in features/episodes/layout.feature or cli feature; no @wip needed beyond the usual.

## Re-scope (2026-10-06, planner) — pilot bean for the Foreman isaac-bean-work machine

The code moved: it is now **isaac-episodes** `src/isaac/session/episodes/cli.clj:137` (`(or (:thread ep) "-")`), and episodes now live under their session (`sessions/<crew>/<session-id>/episodes/<id>/episode.edn`), where records carry `:session-id` and no `:thread`. Print `(or (:session-id ep) (:thread ep) "-")`.

## Acceptance

- isaac-episodes `features/episodes/live.feature` — "episodes list shows a migrated episode's session id (isaac-8uno)".
- The rest of live.feature (including "episodes list shows the crew's chain") stays green.

feature-baseline: isaac-episodes c4c399a8d60318aec9dfc445caa4d5d81279c4e2
feature-blob: isaac-episodes features/episodes/live.feature 1739f9aeab5a6307191f80f6211e64b6163b0cd8 351


## Landed on main (2026-10-06)

main-sha: isaac-episodes ac0f1b510f146d4be0128b199ee200d06e3ee61e

Verified: bb ci (235 specs, 105 feature examples); bb bean-gate verify isaac-8uno PASS.
