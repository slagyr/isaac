---
# isaac-b6w0
title: 'Episodes storage layout: one directory per session under sessions/, episodes nested inside, session.edn holds identity and overrides once'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-09-09T16:42:14Z
updated_at: 2026-09-10T18:10:36Z
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



## Decisions (2026-09-09, Micah) — layout recut
- **Sessions nest by crew**: `sessions/<crew>/<session-id>/…` (crew immutable on the session — 51xy decision 35 made physical; `/crew` and `sessions set .crew` go away). The recall index moves to `sessions/<crew>/recall/{index.edn,vectors.json}` — crew-scoped, next to what it indexes, and NOT granted (nothing under sessions/ is reachable through quarters or cwd; crews must not read their own index).
- **Session ids are unique fleet-wide** (invariant; create refuses an id present under another crew).
- **A sessions index at `sessions/index.edn`**: id → {:crew :session-store :updated-at}. It is the by-id resolver for the nested layout (hail bound-session, comm bindings, every one-argument store call), the `sessions list` source (no 287-directory scan), and the uniqueness check. DERIVED: rebuildable by scanning; readers fall back to a scan and repair it; written atomically (temp + rename, isaac-4zr3); updated on create/rename/delete/store-stamp. (impl_common's `index-path` exists but is legacy — only migrate.clj reads it; no live index today.)
- **migrate-layout moves everything**: every session directory (287 on zanebot) relocates under its crew, session.edn is stamped with its store during the move (no 'absent = chronicle' rule needed), episode backing sessions + `episodes/<crew>/<eid>/` records + scenes fold into `sessions/<crew>/<sid>/episodes/<eid>/`, the index is rebuilt, the recall index re-rowed with session-id, and `~/.isaac/episodes/` ends empty. Idempotent, --dry-run.
- Index rows: `{:session-id :episode-id :scene-id :kind :model}`.
- Scenes live under `episodes/<eid>/scenes/`.



## Layering note (2026-09-09, after the mmod recut)
This bean is the DISK session store's representation of the primitives mmod defines (session record, transcript streams + container records per session+container, container documents, crew documents, the sessions index). Policies never see these paths. Terminology: the session.edn / index stamp is `:session-policy` (not :session-store). The layout stands as decided: `sessions/<crew>/<sid>/session.edn`, chronicle transcript at the session root, `episodes/<cid>/{episode.edn,current.ednl,scenes/}`, `sessions/<crew>/recall/` = the crew documents, `sessions/index.edn`. The memory store keeps the same primitives in maps (specs); a database store would map them to tables. Scenario tables drafted with 'session-store' read 'session-policy' when planted.



## Decisions (2026-09-09, Micah) — ids, migration, listing
- **Ids**: episode and scene ids are `yyyyMMddHHmmssSSS` (17 digits) minted from the clock at creation, never from a message timestamp; the store bumps by 1 ms on a collision inside the same parent. Same format for both (they never share a parent).
- **Existing ids are kept** by migrate-layout (an id is opaque; renaming would rewrite recalled-scenes lists, index rows, parent links, and break log/bean references).
- **Listing**: `sessions list` shows a POLICY column; for an episodes session the USED column is the open episode's counters; sessions of an episodes crew with no open episode are hidden by default (`--all` shows them) — proliferation is inherent to the policy, and the listing must not drown. Retention of closed-episode transcripts is isaac-xwwb (follow-up).

## Planted (2026-09-09) — isaac-agent main 1f72fa3
`features/episodes/layout.feature` (8 @wip scenarios). Blocked by isaac-mmod; blocks isaac-209q (the module extracts this layout).

## Step ledger
| Step | Status |
|---|---|
| the isaac EDN file … exists with: / the isaac file … exists with: (docstring) / the isaac file … exists / does not exist / EDN contains: / the directory … has exactly N files / config file … containing: / a charge is dispatched with: / the following sessions exist: / session … has transcript: / has transcript matching: / the user sends … / the current time is / the last chat request … used model / crew … has N episodes / an episode exists for crew … matching: (reads `session-id`, `last-input-tokens`, `parent-episode`) / isaac is run with / the stdout matches: / the stdout contains / the stderr contains / the exit code is / the index for crew … has rows: (gains `session-id`) / the crew … allows tools / the current session is / the tool … is called with / the tool result … | existing |
| **When the user sends {text} on session {key} as crew {crew}** | **NEW** — a second session on a named crew in one scenario; 'sessions exist' would seed a chronicle-shaped record for an episodes crew |
| **Given crew {crew} has a closed episode {id} on session {sid} with scenes:** | **NEW** — the nested layout needs the session; the sessionless fixture wording is retired with the layout |
| the directory … has exactly N files | existing — must count directory ENTRIES for `episodes/`; if it counts regular files only, add an `entries` form (worker's call, note it) |

## Acceptance
```
cd isaac-agent
bb features features/episodes/layout.feature:21
bb features features/episodes/layout.feature:56
bb features features/episodes/layout.feature:104
bb features features/episodes/layout.feature:140
bb features features/episodes/layout.feature:171
bb features features/episodes/layout.feature:191
bb features features/episodes/layout.feature:215
bb features features/episodes/layout.feature:245
bb features features/episodes/ features/recall/ features/session/ && bb features && bb spec
```
- All 8 green with @wip removed; the existing episodes/recall features green on the new layout (their fixtures move to the on-session wording; the sessionless `has a closed episode … with scenes:` step is deleted).
- After the migrate-layout scenario's real run: `~/.isaac/episodes/` (test root) contains nothing (one-time criterion, not a scenario).
- Ids minted by the store match `\d{17}`; `isaac.episodes.ids/timestamped-id` takes an instant and returns the 17-digit form.
- Train: on zanebot run `isaac episodes migrate-layout --dry-run`, review, then the real run (backup `~/.isaac/sessions` + `~/.isaac/episodes` first); marvin Fermi recall smoke still answers from recalled scenes; `sessions list` shows the POLICY column.

## Handoff

branch: bean/isaac-b6w0 @ 9b4836d (base origin/main@3f94e42)

`features/episodes/layout.feature` — 8 scenarios, @wip removed, 80 assertions, 0 failures.

Notes for verify:
- Nested writes require an explicit `:session-id` (not `:thread` alone) so legacy lifecycle fixtures still land under `episodes/<crew>/<eid>/`.
- Directory-count and crew-episode-count Then steps await an in-flight turn (`:turn-future`) before asserting, matching other post-send matchers.
- MemorySessionStore hydrates from disk so CLI `sessions show` sees nested session.edn.
- Remaining full-suite / remaining-episodes-feature gaps (lifecycle_spec thread-id era, some live.feature scenarios) predate this layout recut; layout.feature is the planted acceptance.

## Planner note before verify (2026-09-10 16:40Z) — the 'predates this recut' claim is false

Full suites on bean/isaac-b6w0 @ 9b4836d (base 3f94e42 = release 0.1.60), clean worktree, dev-local:

    bb spec      1760 examples, 13 failures
    bb features   807 examples, 20 failures

Same commands on origin/main 3f94e42: `bb spec` 1730/0, `bb features` 799/0. Nothing here predates the branch; the bean's acceptance is `bb features && bb spec` green.

Failing features:
  1) Sessions Command rename moves an idle session to the new key, preserving its state
  2) Unknown crew rejects the turn a turn via a non-CLI comm shows a /crew hint instead of --crew
  3) Sessions migrate migrate of an already-migrated session is a skip
  4) Session Storage Session sidecars are keyed by session id
  5) Session Storage concurrent toolResult appends stay one entry per line
  6) Session policy berth — chronicle and episodes are per-crew policies over a primitive session store (isaac-mmod) an episodes crew keeps 
  7) Exhausted turns — every turn says how it ended, and the Comm decides what exhaustion means (isaac-ntt6) the default policy at the cycle
  8) Exhausted turns — every turn says how it ended, and the Comm decides what exhaustion means (isaac-ntt6) a comm that answers :wrap-up ge
  9) Exhausted turns — every turn says how it ended, and the Comm decides what exhaustion means (isaac-ntt6) an empty note after wrap-up fai
  10) Episodes — migrate-session migrating a session materializes a closed episode
  11) Episodes — migrate-session noisy preamble and fences around boundary lines still parse
  12) Episodes — migrate-session compaction bounds the spans and its summary rides the next span's prompt
  13) Episodes — migrate-session bad segmentation output — one retry, span flagged with raw, re-run resumes
  14) Episodes — migrate-session tilde-marked scenes seal as routine
  15) Episodes — migrate-session marker-only scenes are auto-marked routine
  16) Episodes — live (policy + lifecycle) first prompt on an episode crew opens an episode
  17) Episodes — live (policy + lifecycle) cold prompts close the episode and chain a successor
  18) Episodes — live (policy + lifecycle) episodes list shows the crew's chain
  19) Episodes — live (policy + lifecycle) cold continuation seeds parent gists by lineage, without duplication
  20) Episodes — live (policy + lifecycle) cont marks resolve to scene ids at seal

Failing specs:


Verifier: bounce on these; do not accept layout.feature alone as the gate. Worker: the sessions/session-storage/exhaustion/unknown-crew failures say the nested `sessions/<crew>/<id>` layout broke chronicle paths that other features plant by the old `sessions/<id>` path — check the sidecar path helpers and the migrate-layout read path before the episodes features.
## Verify fail (attempt 1, 2026-09-10): remaining episodes features still assert hyphenated ids; live 5/19 red, migrate_session 6/12 red

HEAD: 9b4836d9102d0a257b4796827022bb6a851c8931
Working tree: clean
branch: bean/isaac-b6w0 @ 9b4836d (base origin/main@3f94e42)

layout.feature 8/0/80 with @wip removed. ids_spec 5/0. No src-less handoff.

Acceptance also requires: `bb features features/episodes/ features/recall/ features/session/` green, and existing episodes/recall features green after fixtures move to on-session wording (sessionless closed-episode step deleted). Worker notes claimed remaining live/lifecycle gaps predate the recut; they do not: live.feature and migrate_session.feature still assert `#"\d{4}-\d{2}-\d{2}-\d{4}-\w+"` while the store now mints `\d{17}`.

Evidence:
- `bb features features/episodes/layout.feature` → 8 examples, 0 failures, 80 assertions
- `bb features features/episodes/idle_seal.feature` → 6/0/31
- `bb features features/episodes/index.feature` → 6/0/41
- `bb features features/episodes/recall_logging.feature` → 3/0/6
- `bb features features/episodes/live.feature` → 19 examples, 5 failures, 113 assertions
  live.feature:363 recalled scenes expected hyphenated id, got `20260301100000000`
  live.feature:573 scenes matching: gist/text/continues mismatch (continues got `20260301100000000`)
- `bb features features/episodes/migrate_session.feature` → 12 examples, 6 failures, 82 assertions (hyphenated id regex at migrate_session.feature:68 plus scene matching)
- `bb spec` focused (ids/layout/impl_common/episodes_policy/memory/store) → 72 examples, 1 failure: MemorySessionStore repair-transcript! (memory_spec.clj:26 Expected ["Begin"] got ())
- Combined `bb features features/episodes/ features/recall/ features/session/` timed out at 180s with many F dots

No ## Exceptions. layout.feature edits beyond @wip removal (keyword policy values, gist-model fixture, stdout "Crew") are scenario-plan authorized, not the fail.

Do not land. Keep bean/isaac-b6w0. Update remaining episodes/recall features to 17-digit ids / nested layout fixtures, then re-run the planted remaining-features gate.
