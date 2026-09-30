---
# isaac-hzw2
title: config_schema.feature relays.value.type gains a required marker after apron 3.2.1 (isaac-3y69 follow-up)
status: draft
type: feature
priority: normal
created_at: 2026-09-30T01:17:28Z
updated_at: 2026-09-30T01:17:28Z
---

isaac-dnib bumped foundation to apron 3.2.1. apron's `doc/required-fields` now resolves `:validations [:present? ...]` keyword refs to required-ness (previously only a literal `present?` fn reference counted) — this is an apron 3.2.1 behavior change, not anything isaac-dnib added to `field-block`/`leaf-block`.

Effect: `features/cli/config_schema.feature` (isaac-3y69), scenario "config schema <table>.value renders the entry fields", drills the `marigold.cnfs.bridge` fixture's `:type` field, which is `:validations [:present? [:registered-in? ...]]` — now correctly flagged `*required`. The scenario's pinned regex `type\s+keyword\s+\[relays\.value\.type\]` no longer matches (there's now a `*required` token between `keyword` and the path).

isaac-dnib could not fix this within its own gate: the fix touches a `.feature` file outside its baseline (`bb bean-gate verify` correctly rejects any worker diff to an un-baselined `.feature` file). Confirmed by testing: reverting just this one line change made the gate pass again; `bb features` on the landed foundation main then shows exactly this one failure.

Fix: update the pinned pattern (e.g. `type\s+keyword.*\[relays\.value\.type\]`) to allow the now-correct `*required` marker. Needs a planner re-baseline of isaac-3y69's feature-blob line for this file before a worker can land the one-line change.
