---
# isaac-5gvq
title: A leftover flat session gets a nested twin on first write after b6w0 — history left behind in sessions/<sid>/
status: todo
type: bug
priority: high
created_at: 2026-09-11T04:15:24Z
updated_at: 2026-09-11T18:22:25Z
parent: isaac-b6w0
---

Repo: isaac-agent (session store write path). Observed on zanebot after the 0.1.63 boot (04:00Z 2026-09-11), before migrate-layout ran: the four worker sessions (isaac-work-1/2, tono-work-1/2, crew scrapper) got sessions/scrapper/<sid>/{session.edn,current.ednl,turn.edn} with a fresh transcript (first entry 04:07Z, parentId nil) while sessions/<sid>/ still holds 0..221.ednl and the pre-boot current.ednl. locate-session prefers the nested dir once its session.edn exists, so the flat history is orphaned, and migrate-layout's already-nested? then skips the flat dir entirely. Likely creator: the turn-marker / update-session! write path using session-dir 3-arity for a session that locate-session still finds flat. Required: every write for a session located flat targets the flat dir (or migrates that session in place first); migrate-layout merges a flat dir whose crew twin exists (segments + flat current.ednl become the next segments under the twin) instead of skipping it. Scenario (@wip → green) in layout.feature. Acceptance: no session ever has two directories; layout.feature green; bb spec && bb features green.

Related sample (2026-09-11): the hooks module runs one-shot turns on sessions named hook:<name> (hook:sleep, hook:activity, …) that are never persisted as session records, yet empty flat dirs sessions/hook:<name>/ reappear with today's mtime — the turn-marker record/clear path creates the flat directory for a session the store does not know. Same family: a write for an unknown/flat session must not create sessions/<sid>/; markers for unpersisted sessions should live under the crew (or nowhere).
