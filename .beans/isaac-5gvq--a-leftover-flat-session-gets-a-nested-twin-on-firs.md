---
# isaac-5gvq
title: A leftover flat session gets a nested twin on first write after b6w0 — history left behind in sessions/<sid>/
status: in-progress
type: bug
priority: high
created_at: 2026-09-11T04:15:24Z
updated_at: 2026-09-12T16:27:14Z
parent: isaac-b6w0
---

Repo: isaac-agent (session store write path). Observed on zanebot after the 0.1.63 boot (04:00Z 2026-09-11), before migrate-layout ran: the four worker sessions (isaac-work-1/2, tono-work-1/2, crew scrapper) got sessions/scrapper/<sid>/{session.edn,current.ednl,turn.edn} with a fresh transcript (first entry 04:07Z, parentId nil) while sessions/<sid>/ still holds 0..221.ednl and the pre-boot current.ednl. locate-session prefers the nested dir once its session.edn exists, so the flat history is orphaned, and migrate-layout's already-nested? then skips the flat dir entirely. Likely creator: the turn-marker / update-session! write path using session-dir 3-arity for a session that locate-session still finds flat. Required: every write for a session located flat targets the flat dir (or migrates that session in place first); migrate-layout merges a flat dir whose crew twin exists (segments + flat current.ednl become the next segments under the twin) instead of skipping it. Scenario (@wip → green) in layout.feature. Acceptance: no session ever has two directories; layout.feature green; bb spec && bb features green.

Related sample (2026-09-11): the hooks module runs one-shot turns on sessions named hook:<name> (hook:sleep, hook:activity, …) that are never persisted as session records, yet empty flat dirs sessions/hook:<name>/ reappear with today's mtime — the turn-marker record/clear path creates the flat directory for a session the store does not know. Same family: a write for an unknown/flat session must not create sessions/<sid>/; markers for unpersisted sessions should live under the crew (or nowhere).

## Work checkpoint (2026-09-12, scrapper@isaac-work-2)

Done: Agent `bean/isaac-5gvq` is pushed through `c51370f`: sidecar updates use the located flat directory, and `locate-session` falls back to the actual flat record when the derived index points at a missing nested record. Focused Agent store specs are green (30 examples, 58 assertions), covering update, transcript append, and marker placement without a nested twin.

In progress/red: Episodes `bean/isaac-5gvq` has a red migration spec for merging a flat directory into an existing nested twin. The plan now includes twins and copies flat frozen/current transcripts to the next nested segment numbers, but MemoryFs retains the source shell and the second migration is not yet a no-op (206 examples, 2 failures). Resume at `isaac-episodes-5gvq/src/isaac/episodes/layout.clj:161`; inspect MemoryFs deletion behavior and make source cleanup observable without regressing ordinary migration. Then add the matching `features/episodes/layout.feature` scenario and run both repos' full gates.
