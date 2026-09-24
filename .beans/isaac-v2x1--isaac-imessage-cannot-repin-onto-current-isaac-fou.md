---
# isaac-v2x1
title: 'isaac-imessage cannot repin onto current isaac-foundation/isaac-agent: 3 lifecycle specs fail'
status: completed
type: bug
priority: normal
created_at: 2026-09-24T18:28:43Z
updated_at: 2026-09-24T21:29:59Z
---

Repo: **isaac-imessage** (`spec/isaac/comm/imessage_lifecycle_feature_spec.clj`),
cause almost certainly in **isaac-foundation**.

## Problem

isaac-imessage pins isaac-agent `510d5b8` and isaac-foundation `8fbeed3`.
Bumping those to isaac-agent `b6eb475` and isaac-foundation `9ab2527`
(isaac-ajlh, which isaac-agent itself pins) breaks three specs:

    1) lifecycle feature wiring registers the comm in comm-registry for delivery on server start
         Expected truthy but was: false
    2) lifecycle feature wiring hot-reload removes comm when slot deleted
         Expected truthy but was: false
    3) lifecycle feature wiring hot-reload updates message-cap in place
         NullPointerException: Cannot read field "state_STAR_" because "comm" is null

The comm never lands in the registry, so `comm-for "imessage"` is nil.

**Isolated to the pin bump.** With deps.edn bumped and the working tree
otherwise clean — no source changes at all — `bb jvm-spec` gives
`50 examples, 3 failures`. Reverting only deps.edn restores
`50 / 0`, `59 / 0`, `19 / 0`. It is not isaac-fkjq / isaac-2zs0 / isaac-clba
work, which was verified green both ways before landing as `045e201`.

No config validation error is logged; the comm simply is not registered. The
specs drive config through `isaac.http.server-steps/server-config-applied` with
the path `comms.imessage.imessage/service`.

Prime suspect: isaac-ruom restructured `:defaults` into entity templates and
moved where a comm's `:crew` is derived from, and isaac-ajlh changed
`demands-a-field?`. One of those changes how a `comms.<id>` slice composes when
applied through the server-config path, which the module's own suite is the
only thing exercising.

## Why it matters

isaac-clba enriched the delivery audit log (`:error`, a real `:target`) in
isaac-agent. **That improvement does not reach the iMessage comm — or discord,
gchat, gmail — until each module repins onto the new isaac-agent.** This bean
is the blocker for iMessage's repin. Worth checking whether the sibling comm
modules hit the same wall.

## Acceptance

- isaac-imessage builds and its full suite passes against current
  isaac-foundation and isaac-agent `main`.
- The root cause is named — if the composition behaviour changed deliberately
  (isaac-ruom), the spec is updated and the change documented; if not, it is a
  foundation regression and gets fixed there, with a foundation-level scenario
  so the next module does not discover it.
- The other comm modules are checked for the same failure.

## Evidence

- isaac-imessage `045e201`, deps.edn still at isaac-agent `510d5b8` /
  isaac-foundation `8fbeed3` — deliberately not bumped, so main stays green.
- isaac-agent `b6eb475` (isaac-clba) pins isaac-foundation `9ab2527`.
- Hosts are unaffected today: a host resolves `isaac.agent` by its own module
  pin, not through isaac-imessage's deps.edn.
