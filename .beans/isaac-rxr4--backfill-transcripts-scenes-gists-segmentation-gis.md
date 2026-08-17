---
# isaac-rxr4
title: 'Backfill: transcripts -> scenes + gists (segmentation + gisting command)'
status: draft
type: task
priority: normal
created_at: 2026-08-17T03:21:48Z
updated_at: 2026-08-17T03:48:36Z
parent: isaac-51xy
blocked_by:
    - isaac-5lri
---

Child of isaac-51xy (episodic memory), phase-1 bean 2. Materialize existing sessions as closed episodes — episode records + scenes + gists — without touching session files. This IS the sessions→episodes migration (the phase-2 "retroactive migration" is thereby already done). Sessions remain live and authoritative; episodes are derived.

## Design (settled 2026-08-16, Micah + planning session)
- **Command:** `isaac episodes migrate-session <session-id>` — session id REQUIRED (one session per invocation; operator-paced on zanebot's large corpus; no --all yet). Never embeds — indexing is bean 3 (`transcripts → migrate → scenes → index → vectors`); live seal-time path is bean 5.
- **1:1 mapping:** one session → one closed episode, tagged migrated. No time-gap splitting — retrieval runs over scenes/gists (own timestamps); episode grouping barely affects it.
- **Storage:** `~/.isaac/episodes/<crew>/` with `episodes.ednl` + `scenes.ednl` — **EDNL** (edn lines, like logs; long-running intent to move formats there — transcripts stay JSONL for now). "Recall" names only the retrieval act, never storage or commands.
- **Episode record (closed, migrated):** id = session id, crew (from session .edn metadata), timestamps, scene ids, :migrated provenance.
- **Scene record:** plain EDN — `:start-id`/`:end-id` (MESSAGE ids from the transcript — provenance only, never a retrieval lookup path; recall injects the scene's stored `:text`), timestamps, seal-reason, distilled `:text`, `:gist`. Scenes are not transcripts; the session transcript stays the only message log.
- **Segmentation pass:** one LLM call per **compaction-span** (processing window, NOT a scene). In: distilled messages with ids + the span's existing compaction :summary (transcripts already carry these — free gist-draft context). Out: strict EDN scenes `{:start-id :end-id :gist}` tiling the span (every message in exactly one scene). The LLM draws multiple scenes per span by topic; a scene never crosses a compaction boundary — identical to the live rule (compaction is a seal trigger, epic Decision 5). No-compaction sessions: size-capped windows (matches live size-cap seal). Bad parse → one retry → flag the span and continue.
- **Resumable/idempotent:** spans already sealed to scenes.ednl are skipped; interrupted migration continues; fully-migrated session is a no-op without --force. Migration is one sequential streaming pass — no random access even on 1M-line transcripts.
- **Gist model:** configurable (default: defaults model); grover plays gist-writer in tests.
- **Distillation (what the gist LLM sees / what :text stores):** user+assistant text kept; each toolCall collapsed to a one-line marker (name + truncated args); tool results dropped; compaction summaries used as span context, not scene text.

## Transcript facts (grounded 2026-08-16)
Session = `~/.isaac/sessions/<key>.jsonl` + sibling `<key>.edn` metadata (carries :crew, :id, :cwd). Lines: `session` header, `message` ({:role :content[]}, stable id/parentId/timestamp), `compaction` (carries :summary). Tool calls are content items type "toolCall".

## Scope
- In: episodes/scenes EDNL writing, distillation, windowing, LLM segmentation+gisting, resume/idempotency, CLI + config (gist model).
- Out: embedding/index (bean 3, isaac-j2p4), live sealing (bean 5), --all batch mode, time-gap episode splitting.

Status draft — design settled; scenario plan next, then promote to todo once scenarios committed @wip.
