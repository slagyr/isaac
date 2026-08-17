---
# isaac-80pq
title: 'migrate-session field defects: auth errors mislabeled, raw response discarded, parser rejects list-form maps'
status: completed
type: task
priority: normal
created_at: 2026-08-17T14:12:49Z
updated_at: 2026-08-17T14:33:51Z
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

## Contract revision (2026-08-17, Micah): replace EDN output with line format
Supersedes defect 3's "normalize list-form maps" fix — do NOT add EDN leniency; replace the contract. The only structured thing we need from the model is boundaries; the gist is free text, which no model can get syntactically wrong.

**The exact ask** (one user message per span):

```
You are segmenting a conversation into scenes: contiguous runs of
messages about one topic. Below is a numbered list of messages.

Output one line per scene, nothing else:
<first>-<last>: <one-sentence gist of what was discussed or decided>

Every message number must fall in exactly one scene, in order,
no gaps. Start a new scene when the topic changes. A single-message
scene is written as e.g. "7-7: ...".

Preceding context (summary of the conversation before this point):
<prior compaction summary, when present>

Messages:
1. [user] ...
2. [assistant] ...
```

**Expected reply, in full:**
```
1-2: wine pairing for the pheasant dinner — settled on a light pinot noir
3-5: regatta schedule — first race Saturday at dawn
```

**Code's side:** regex `^(\d+)-(\d+):\s*(.+)$` per line (accept bare `7` as `7-7` — one alternation); lines that don't match are IGNORED (preamble/fences/blanks are non-fatal); tiling validation over 1..N unchanged and remains the correctness gate; ordinals→message-ids unchanged; line remainder = gist. A malformed line drops one scene and breaks tiling → span flagged with a diagnosable :raw (defect 2), instead of the whole response dying on syntax.

**Ripples:** SEGMENT_INSTRUCTIONS + parse-scenes rewritten; feature scenarios' queued gist responses move from EDN to line form (grover fixtures get simpler); the list-form-maps acceptance scenario becomes "reply with preamble + fenced/noisy lines around valid boundary lines → still parses". Defects 1 (auth surfacing + abort, no retry/flag), 2 (persist+log :raw on flag), 4 (1-based numbering everywhere) unchanged.
