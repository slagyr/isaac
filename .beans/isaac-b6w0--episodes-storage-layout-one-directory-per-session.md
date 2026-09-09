---
# isaac-b6w0
title: 'Episodes storage layout: one directory per session under sessions/, episodes nested inside, session.edn holds identity and overrides once'
status: draft
type: feature
priority: high
created_at: 2026-09-09T16:42:14Z
updated_at: 2026-09-09T16:42:14Z
blocking:
    - isaac-209q
blocked_by:
    - isaac-mmod
---

Repo: isaac-agent (episodes store implementation, migration command). Sequenced AFTER isaac-mmod (the session-store berth) and BEFORE isaac-209q (extraction), so the module extracts the final layout. Planning 2026-09-09 (Micah + planner).

## Problem
Today an episode's backing transcript is created as a full session under `~/.isaac/sessions/<episode-id>/` with its own copy of crew, cwd, tags, pins and counters, while the episode record and scenes live in a second tree under `~/.isaac/episodes/<crew>/<episode-id>/`. The session (the surface's identity, stable across episodes) has no directory of its own; overrides are duplicated per episode; two trees must be kept in step.

## Decisions (2026-09-09, Micah)
1. **Layout**
```
~/.isaac/sessions/<session-id>/
  session.edn                 identity + overrides, once: crew, cwd, tags, model/effort/context-mode/compaction
                              pins, and :session-store (which store owns this directory)
  current.ednl, 0.ednl …      chronicle only: the transcript and its rotated segments
  episodes/<episode-id>/      episodes only: one directory per episode
    episode.edn               status, started-at/ended-at, parent-episode, scene-ids, migrated-from,
                              and the transcript-level state (token counters, compaction breaker)
    current.ednl              that episode's transcript
    scenes/                   sealed scenes + gists
```
2. **Both kinds share `sessions/`; the session.edn `:session-store` stamp says which store owns the directory.** Each store reads only its own layout; `isaac sessions list` lists both kinds with a store column; a crew that switches store never misreads old directories (no inference from the presence of `episodes/`).
3. **The recall index stays crew-scoped** (scenes are recalled across all of a crew's sessions); index entries point at session-id + episode-id + scene-id. Scenes live with their episode.
4. **Counter split**: session.edn = what is true across episodes (identity, overrides); episode.edn = what is true of one transcript (tokens, compaction breaker, drift ratio). For chronicle both live in session.edn because the session is the transcript. Behaviour resolution reads session.edn once; episodes no longer copy pins into backing transcripts.
5. **Clean cutover with a migration command**: `isaac episodes migrate-layout` (or a mode of the existing `isaac episodes migrate`) moves each existing episode's backing session dir + episode record + scenes into `sessions/<session-id>/episodes/<episode-id>/`, writes session.edn once per session-id (from the most recent backing session's pins), rewrites index entries, and leaves nothing under `~/.isaac/episodes/`. Idempotent; dry-run flag; run on zanebot at the train (marvin + tono crews).

## Scope
- IN: episodes store on-disk layout + reads/writes; chronicle stamp (`:session-store :chronicle` written on create; absent = chronicle for pre-existing dirs); `sessions list` store column; `episodes list/index/close` reading the new layout; recall index entry shape; migration command; the ACL's `:quarters`/cwd unaffected.
- OUT: arcs; the extraction itself (209q); any change to the SessionStore protocol beyond what mmod added.

## Scenario plan (to be drafted with Micah; @wip in isaac-agent until 209q moves the episodes ones)
1. a cold open on an episodes crew creates sessions/<id>/session.edn once and episodes/<eid>/ beneath it (no episode dir under sessions/ root, nothing under ~/.isaac/episodes/)
2. a successor episode after compaction lands as a sibling under the same session directory; session.edn is untouched; the old episode's episode.edn carries its final counters
3. session.edn is the single source of overrides: a session-level pin set once applies to every episode of that session (no copy in episode.edn)
4. the chronicle stamp: a chronicle crew's session directory carries :session-store :chronicle and its transcript at the top level; `sessions list` shows the store column for both kinds
5. recall finds a scene by session-id + episode-id + scene-id after the move (index entries)
6. migrate-layout moves a legacy episode (backing session + episodes/<crew>/<id>/ record + scenes) into the new layout, idempotent, dry-run prints the plan
Fixtures: Marigold cast (cordelia crew, lantern-room session). Steps: mostly existing (isaac EDN file exists with, sessions exist, charge dispatched, episode exists for crew matching, isaac file exists / does not exist, isaac is run with); expected NEW: `the isaac directory … exists` (if absent), a scene-lookup assertion for 5.

## Acceptance (sketch until scenarios are planted)
- all planted scenarios green with @wip removed; `bb features && bb spec` green
- `isaac episodes migrate-layout --dry-run` then real run on zanebot at the train; `ls ~/.isaac/episodes` empty afterwards; marvin recall smoke (Fermi) still answers from recalled scenes
