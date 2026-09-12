---
# isaac-5gvq
title: A leftover flat session gets a nested twin on first write after b6w0 — history left behind in sessions/<sid>/
status: completed
type: bug
priority: high
created_at: 2026-09-11T04:15:24Z
updated_at: 2026-09-12T17:50:17Z
parent: isaac-b6w0
---

Repo: isaac-agent (session store write path). Observed on zanebot after the 0.1.63 boot (04:00Z 2026-09-11), before migrate-layout ran: the four worker sessions (isaac-work-1/2, tono-work-1/2, crew scrapper) got sessions/scrapper/<sid>/{session.edn,current.ednl,turn.edn} with a fresh transcript (first entry 04:07Z, parentId nil) while sessions/<sid>/ still holds 0..221.ednl and the pre-boot current.ednl. locate-session prefers the nested dir once its session.edn exists, so the flat history is orphaned, and migrate-layout's already-nested? then skips the flat dir entirely. Likely creator: the turn-marker / update-session! write path using session-dir 3-arity for a session that locate-session still finds flat. Required: every write for a session located flat targets the flat dir (or migrates that session in place first); migrate-layout merges a flat dir whose crew twin exists (segments + flat current.ednl become the next segments under the twin) instead of skipping it. Scenario (@wip → green) in layout.feature. Acceptance: no session ever has two directories; layout.feature green; bb spec && bb features green.

Related sample (2026-09-11): the hooks module runs one-shot turns on sessions named hook:<name> (hook:sleep, hook:activity, …) that are never persisted as session records, yet empty flat dirs sessions/hook:<name>/ reappear with today's mtime — the turn-marker record/clear path creates the flat directory for a session the store does not know. Same family: a write for an unknown/flat session must not create sessions/<sid>/; markers for unpersisted sessions should live under the crew (or nowhere).

## Work checkpoint (2026-09-12, scrapper@isaac-work-2)

Done: Agent `bean/isaac-5gvq` is clean and pushed at `d0668ae` (base `origin/main@a6c27f8`): located flat sessions receive sidecar updates, transcript appends, and markers without creating nested twins; stale index rows fall back to the actual flat record; unpersisted hook sessions no longer create flat marker directories. Focused Agent specs are green (36 examples, 86 assertions), storage/marker features are green (20 examples, 44 assertions), and full Agent specs are green (1598 examples, 3296 assertions). Episodes `bean/isaac-5gvq` is clean and pushed at `b06a4b9` (base `origin/main@089a764`): migrate-layout includes flat/nested twins, preserves flat frozen/current history as the next nested frozen segments, deletes flat files, remains idempotent, and pins the Agent repair. Full Episodes specs are green (206 examples, 552 assertions); layout acceptance is green (11 examples, 110 assertions).

Acceptance comparison: full Episodes features are 79 examples / 3 failures / 521 assertions; clean `origin/main` is 78 / the identical 3 failures / 515 assertions (embedding config validation ×2 and recall log ×1), so the added scenario is green with no regression. Full Agent features hit the repository's 180-second timeout with one progress failure; full Agent specs initially showed two alternating async harness flakes, but immediate focused `session_steps_spec` is green (16/0/36) and a subsequent full spec is green. Implementation is ready for baseline-aware verification. Resume at `isaac-episodes-5gvq/src/isaac/episodes/layout.clj:147` for migration review and `isaac-agent-5gvq/src/isaac/session/store/impl_common.clj:370` for write-path review.

## Landed on main (2026-09-12)

main-sha: isaac-agent 2fd69fee7f0601d46972a302e3879dd8d144990d
main-sha: isaac-episodes ffb94ff5cd42bd670cffd0769a410b3699cbf3c2
