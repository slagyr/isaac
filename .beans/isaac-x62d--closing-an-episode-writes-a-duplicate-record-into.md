---
# isaac-x62d
title: Closing an episode writes a duplicate record into the legacy episodes/<crew>/<eid>/ tree — migrate-session! materializes without :session-id
status: completed
type: bug
priority: high
created_at: 2026-09-11T15:10:21Z
updated_at: 2026-09-11T15:18:57Z
parent: isaac-b6w0
---

Repo: isaac-agent. Seen on zanebot after the layout migration (2026-09-11): episodes/ came back with marvin/20260911044719928, 20260911044806072, 20260911054227339 — each with episode.edn (:status :closed, :migrated-from <session id>, no :session-id) plus the scene .md files — while the same episodes also exist nested under sessions/marvin/<sid>/episodes/<eid>/ with the live record. Cause: lifecycle/close-episode! calls migrate/migrate-session!, which builds the closed episode map with :migrated-from session-id but no :session-id (migrate.clj ~223), so store/write-episode! takes the legacy path; the lifecycle then re-merges :session-id and writes the nested copy. Every close leaves a legacy duplicate; list-episodes dedupes by id (nested first) so listings look fine, but scene reads consult both trees and the legacy tree regrows after every migrate-layout.

## Required
- migrate-session! writes the materialized episode with :session-id (the backing session's id) so it nests; the CLI migrate-session path gets the same.
- The lifecycle's pre-write of (assoc existing :migrated-from episode-id) must not fall back to the legacy path either (existing already carries :session-id from a nested read; guard anyway).
- Assertion added to the existing live.feature close/chain scenario and to migrate_session.feature: after a close (or a migrate-session), the legacy episodes/<crew> directory does not exist.
- Ops: after deploy, remove ~/.isaac/episodes on zanebot once every legacy id is confirmed to have a nested twin (they are duplicates).

## Acceptance
bb features features/episodes/ green; bb spec && bb features green; on zanebot after deploy + cleanup, closing an episode creates nothing under ~/.isaac/episodes.

## Summary of Changes (planner, 2026-09-11)

Landed on isaac-agent main (squash 3cd7d8d; release 0.1.67 = 4737cf4ce80d5918a26dc7c2f1ac2aaf4f66fce9). migrate-session! takes :nest-under from close-episode! ((or :session-id :thread) of the open record) and stamps :session-id, so the closed record nests where the live record is; the migrated-from pre-write nests the same way; the CLI migrate-session nests under the migrated session. Assertions added to live.feature (cold close/chain) and migrate_session.feature: episodes/<crew> does not exist afterwards. Step fix: ensure-current-episode! derives crews from the nested sessions tree. Gates: full features 831/0, spec 1794/1 (documented file_spec intermittent). Ops: deploy, then remove ~/.isaac/episodes after confirming each legacy id has a nested twin.
