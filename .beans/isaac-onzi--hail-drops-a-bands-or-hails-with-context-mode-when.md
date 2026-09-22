---
# isaac-onzi
title: Hail drops a band's or hail's :with-context-mode when building the delivery charge
status: todo
type: bug
priority: normal
tags:
    - hail
created_at: 2026-09-22T21:27:18Z
updated_at: 2026-09-22T21:27:18Z
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
