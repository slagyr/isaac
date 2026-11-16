---
# isaac-tpa5
title: 'isaac-dream: retire habits complained about on two days; propose retiring faded ones'
status: draft
type: feature
priority: normal
tags:
    - dream
created_at: 2026-11-16T14:49:07Z
updated_at: 2026-11-16T14:49:07Z
parent: isaac-pcm3
blocked_by:
    - isaac-o4di
    - isaac-wh9y
---

Repo: **isaac-dream**. Split from isaac-o4di (Micah, 2026-11-15/16). Spec: `features/dream/retire.feature`.

## What

After a dream records what it found, it checks the crew's active habits:

- **Complaints on two different UTC days** (two `:contradicted` events on different days, counting the one just recorded): the dream retires the habit itself. History event `:retired` with `:reason :contradicted`; the digest says it was retired and gives `dream approve <id> --crew <crew>` to restore it. Complaints on the same day count once.
- **Faded below the floor**: strength (isaac-o4di) under `dream.strength.floor` (default 0.5) appends a `:retire-proposed` event and the digest offers `dream retire <id> --crew <crew>`. Proposed once: not again while a `:retire-proposed` event is newer than the habit's last approval or reinforcement.
- **Pinned habits** are never retired or proposed.
- Approving a retired habit makes it active again (adds an `:approved` event, +2 strength).

Config: `dream.strength.floor` on the crew dream schema (defaults from `defaults.crew.dream`).

## Acceptance

`bb features features/dream/retire.feature` green with `@wip` removed (all 5 scenarios), plus `bb spec` and full `bb features` green. Bump the module version.
