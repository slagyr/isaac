---
# isaac-rxr4
title: 'Backfill: transcripts -> scenes + gists (segmentation + gisting command)'
status: in-progress
type: task
priority: normal
created_at: 2026-08-17T03:21:48Z
updated_at: 2026-08-17T04:29:16Z
parent: isaac-51xy
blocked_by:
    - isaac-5lri
---

Child of isaac-51xy (episodic memory), phase-1 bean 2. Materialize existing sessions as closed episodes — episode records + scenes + gists — without touching session files. This IS the sessions→episodes migration (the phase-2 "retroactive migration" is thereby already done). Sessions remain live and authoritative; episodes are derived.

## Design (settled 2026-08-16, Micah + planning session)
- **Command:** `isaac episodes migrate-session <session-id>` — session id REQUIRED (one session per invocation; operator-paced on zanebot's large corpus; no --all yet). Never embeds — indexing is bean 3 (`transcripts → migrate → scenes → index → vectors`); live seal-time path is bean 5.
- **1:1 mapping:** one session → one closed episode, tagged migrated. No time-gap splitting — retrieval runs over scenes/gists (own timestamps); episode grouping barely affects it.
- **Storage (revised same session — dir per episode, file per scene):**
  ```
  ~/.isaac/episodes/<crew>/<episode-id>/
    episode.edn        ; thin record: crew, timestamps, scene ids, :migrated-from <session-id>
    <scene-id>.edn     ; one immutable EDN file per sealed scene
    transcript.ednl    ; phase 2, live episodes only — NOT written by migration
  ```
  Scenes are immutable after seal → write-once single files (no append coordination, no partial-line corruption); retrieval reads exactly the hit scene's file; the future open scene (bean 5) is one small file rewritten per turn. EDNL survives only as the future live transcript format; everything migration writes is single-EDN-record files. "Recall" names only the retrieval act, never storage or commands.
- **Ids are timestamped:** `<yyyy-MM-dd-HHmm>-<chaos>` (few random base36 chars for same-minute uniqueness) for BOTH episode and scene ids — chronological `ls` for free. Migrated episodes use the session's FIRST-MESSAGE time (their real era), not migration time; scene ids use their span's first-message time.
- **Episode record (closed, migrated):** timestamped id, crew (from session .edn metadata), timestamps, scene ids, `:migrated-from <session-id>` provenance.
- **Scene record:** plain EDN — `:start-id`/`:end-id` (MESSAGE ids from the transcript — provenance only, never a retrieval lookup path; recall injects the scene's stored `:text`), timestamps, seal-reason, distilled `:text`, `:gist`. Scenes are not transcripts; the session transcript stays the only message log.
- **Segmentation pass:** one LLM call per **compaction-span** (processing window, NOT a scene). In: distilled messages with ids + the span's existing compaction :summary (transcripts already carry these — free gist-draft context). Out: strict EDN scenes `{:start-id :end-id :gist}` tiling the span (every message in exactly one scene). The LLM draws multiple scenes per span by topic; a scene never crosses a compaction boundary — identical to the live rule (compaction is a seal trigger, epic Decision 5). No-compaction sessions: size-capped windows (matches live size-cap seal). Bad parse → one retry → flag the span and continue.
- **Resumable/idempotent:** spans whose scenes are already sealed to files are skipped; interrupted migration continues; fully-migrated session is a no-op without --force. Migration is one sequential streaming pass — no random access even on 1M-line transcripts.
- **Gist model:** configurable (default: defaults model); grover plays gist-writer in tests.
- **Distillation (what the gist LLM sees / what :text stores):** user+assistant text kept; each toolCall collapsed to a one-line marker (name + truncated args); tool results dropped; compaction summaries used as span context, not scene text.

## Transcript facts (grounded 2026-08-16)
Session = `~/.isaac/sessions/<key>.jsonl` + sibling `<key>.edn` metadata (carries :crew, :id, :cwd). Lines: `session` header, `message` ({:role :content[]}, stable id/parentId/timestamp), `compaction` (carries :summary). Tool calls are content items type "toolCall".

## Scope
- In: episode/scene EDN file writing, distillation, windowing, LLM segmentation+gisting, resume/idempotency, CLI + config (gist model).
- Out: embedding/index (bean 3, isaac-j2p4), live sealing (bean 5), --all batch mode, time-gap episode splitting.


## Drafting-session decisions (2026-08-16, scenario review)
- **Segmentation LLM speaks SPAN-LOCAL ORDINALS, not message ids** — prompt numbers the span's distilled messages 1..N; response scenes `{:start :end :gist}` must tile 1..N exactly (validation catches drift); code resolves ordinals → message ids at seal. Rationale: opaque-id echo is the weakest link (silent mis-spans); ordinals are near-immune and cheaper. Ordinals count ALL span messages including dropped-from-text toolResults (tiling over, not renumbering around).
- **Tool marker format:** `(tool <name> <arg-summary>)` inline in scene :text where the call happened.
- **Compaction summary rides the FOLLOWING span's prompt** as preceding-context; span 1 of a session has none.
- **Episode :status:** `:partial` (flagged spans remain) | `:closed`. Partial migration exits 1 but persists sealed scenes; plain re-run resumes flagged/missing spans only ("resumed"); fully-migrated re-run is exit-0 no-op ("already migrated"); `--force` re-runs the LLM pass and replaces in place.
- **Retry:** one retry per span on unparseable output, then flag and continue with remaining spans.
- **Config:** `:episodes {:gist-model <model-ref>}` (ordinary chat model — reuses models/providers; default: defaults model). Token cost ≈ one pass over the DISTILLED corpus (tool payloads stripped), once; point gist-model at local ollama on zanebot for zero API cost.
- **Fixture dialect assumptions to verify at implementation** (adjust cells to real dialects, semantics unchanged): `summary` column on compaction rows in the transcript-fixture table; `messages` path form in `the last LLM request matches:`; queued-response content cell holding EDN; crew `cordelia` may need a minimal crew config in Background.

## Scenarios
Committed @wip: isaac-agent `features/episodes/migrate_session.feature` (7 scenarios, commit 2e98dd5).

New steps (4): `an episode exists for crew {crew} matching:` (reads episode.edn, regex cells) · `that episode has scenes matching:` (scene files in id order; count+order+gist+text) · `scene {n} of that episode does not contain {string}` (absence — payload-dropped claim) · `crew {crew} has {n} episodes` (no-duplicate claim). All other steps reused (verified against session/config/ollama features).

## Acceptance
- `bb features features/episodes/migrate_session.feature` — all 7 pass, @wip removed on completion
- `bb features` and `bb spec` — full suites stay green
