---
# isaac-80pq
title: 'migrate-session field defects: auth errors mislabeled, raw response discarded, parser rejects list-form maps'
status: todo
type: task
created_at: 2026-08-17T14:12:49Z
updated_at: 2026-08-17T14:12:49Z
parent: isaac-51xy
---

Found 2026-08-17 field-testing isaac-rxr4 locally (first real-model contact). Three defects in isaac.episodes.segment/migrate:

1. **Provider errors mislabeled as parse failures.** dispatch-chat error maps (observed: :auth-missing from openai-codex) flow through response-text as "" and are reported as "span N flagged: unparseable segmentation output", after a pointless retry. Required: an {:error ...} chat response surfaces the provider + error (e.g. "openai-codex: auth-missing"), aborts the run (exit 1) without flagging spans as parse failures — auth won't fix itself between spans.
2. **Raw response discarded on flag.** segment-span! captures :raw but the flag path drops it — flagged spans are undiagnosable. Required: log the raw text (warn, truncated) AND persist it on the flagged-span record so a later look can see what the model said.
3. **parse-scenes rejects list-form maps.** Real repro (llama3.2, verified by direct curl): ((:start 1 :end 2 :gist "wine pairing") (:start 3 :end 4 :gist "regatta schedule")) — correct ordinals/tiling/gists, paren-wrapped kv lists instead of {} maps; every? map? fails and a perfect response is flagged. Required: normalize keyword-led even-length lists to maps before validation (fence-stripping already exists).
4. Cosmetic: stderr says "span 1" while summary says "flagged spans: [0]" — pick 1-based everywhere.

## Acceptance (extend features/episodes/migrate_session.feature)
- New scenario: gist model returns list-form maps → migration succeeds (reuse quiet-regatta shape with list-form queued response).
- New scenario: chat error from provider (grover can script an error response? if not, stub via simulate) → stderr names provider+error, exit 1, no "unparseable" wording, no span flagged, no retry consumed.
- Updated failure scenario: flagged span's record carries :raw (assert via episode/scene step or log assertion per logging skill).
- 1-based span numbering consistent in stderr + summary.
- bb features features/episodes/migrate_session.feature green; bb spec green.

Context: gist model config is :episodes {:gist-model <ref>}; local field config used ollama/llama3.2. Grover-only testing gave false confidence on output leniency — keep the real-repro list-form as the fixture.
