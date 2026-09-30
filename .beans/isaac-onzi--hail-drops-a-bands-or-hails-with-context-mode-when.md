---
# isaac-onzi
title: 'Queued turns drop :with-context-mode: isaac.turn.worker/wake-charge never forwards it'
status: todo
type: bug
priority: normal
tags:
    - hail
created_at: 2026-09-22T21:27:18Z
updated_at: 2026-09-30T14:05:33Z
blocked_by:
    - isaac-zdnx
---

## Problem

`isaac.hail.delivery-worker/delivery-charge` (isaac-hail
`src/isaac/hail/delivery_worker.clj` ~L516) builds the turn's charge from the
delivery's behavioral override with `:crew` and `:model-override` only. The
override map already carries `:context-mode` when a band or hail sets
`:with-context-mode` (`session.frequencies/behavioral-override`, router.clj
L192), but it is dropped here, so a band-level context mode never reaches the
turn. Same gap the CLI had in isaac-zdnx.

## Change

Pass `:context-mode-override (:context-mode override)` to `charge/build` —
the key isaac-zdnx adds to `isaac.charge/build` (agent main after PR #4).
Needs isaac-hail's agent pin bumped to a main sha that carries it.

## Acceptance

- Scenario in isaac-hail (hail features, Marigold): a band with
  `:with-context-mode :reset` delivering to a session whose crew has no
  context-mode → the turn resolves `:context-mode :reset`; and a hail with
  `:with-context-mode :full` beats a band's `:reset`.
- `bb spec` / `bb features` / `bb ci` green in isaac-hail with the bumped pin.

## Related

isaac-zdnx (CLI side, blocks this), isaac-dgod trial notes (2026-09-22).

## Triage update (2026-09-30, planner, approved by Micah)

Likely repo is now **isaac-agent**. isaac-hail's delivery_worker.clj is gone; hail carries :with-context-mode in :frequencies to Agent's TurnStore, but isaac.turn.worker/wake-charge forwards only :with-crew and :with-model into charge/build. The target shape already exists: charge/build accepts :context-mode-override, and bridge/prompt_cli.clj:267 maps it for the CLI (isaac-zdnx). The fix is the same one-line mapping in wake-charge; scenarios should move to isaac-agent's turn queue features.
