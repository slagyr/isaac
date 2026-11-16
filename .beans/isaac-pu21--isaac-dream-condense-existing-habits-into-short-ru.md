---
# isaac-pu21
title: 'isaac-dream: condense existing habits into short rules as approvable revisions (keep id, history, strength)'
status: draft
type: feature
priority: normal
tags:
    - dream
created_at: 2026-11-16T16:22:45Z
updated_at: 2026-11-16T16:22:45Z
parent: isaac-pcm3
blocked_by:
    - isaac-4wsu
---

Repo: **isaac-dream**. Follows isaac-4wsu. Micah approved Zane's 38 habits on 2026-11-15 with long lessons (avg ~420 chars, backstory included). Once lessons are short rules with a separate why, the existing habits need condensing without losing their ids, history or strength.

## Direction (draft)

- **Revision, not replacement.** A reworded habit keeps its id. A revision is stored on the habit as a pending `:revision {:lesson … :why …}` with a `:revision-proposed` event; the active lesson keeps working until the operator approves. `isaac dream approve <id>` on a habit with a pending revision applies it (`:revised` event, old lesson kept in history); `dream reject <id>` drops the revision. Approving with `--lesson` sets the wording by hand.
- **`isaac dream condense --crew <id>`**: one model call over the crew's active habits whose lesson is long or has no why; the model returns `{id, lesson, why}` per habit; code stores each as a pending revision and the digest lists them.
- The nightly dream may also propose revisions (`revise: [{id, lesson, why}]`) when a habit's wording no longer fits; same approval.

## Open

- Threshold for "long": over 300 chars (the new cap) or no `:why`.
- Does a revision reset decay? Probably not; history and strength carry over.
