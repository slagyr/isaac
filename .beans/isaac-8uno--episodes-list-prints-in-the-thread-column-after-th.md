---
# isaac-8uno
title: episodes list prints '-' in the thread column after the layout migration — should show :session-id
status: todo
type: bug
priority: low
created_at: 2026-09-11T04:43:30Z
updated_at: 2026-09-11T04:43:30Z
parent: isaac-b6w0
---

Repo: isaac-agent (src/isaac/episodes/cli.clj list). After migrate-layout, episode.edn carries :session-id and no :thread (move-episode! dissocs :thread); `isaac episodes list --crew marvin` now prints '-' where it used to print the thread id (e.g. 2026-09-10-1524-qc65  closed  -  51 scenes). Print :session-id (falling back to :thread for unmigrated records). Scenario in features/episodes/layout.feature or cli feature; no @wip needed beyond the usual.
