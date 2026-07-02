---
# isaac-084j
title: 'Resolve isaac.agent version conflict: isaac-imessage pins agent 0.1.0'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-07-02T22:05:29Z
updated_at: 2026-07-02T22:22:56Z
---

## Problem

`isaac modules list` on zanebot warns:

    ⚠  1 version conflict — one version loaded; the rest dropped
    MODULE       VERSION  REQUIRED BY          LOADED
    isaac.agent  0.1.0    isaac.comm.imessage
    isaac.agent  0.1.5    isaac.comm.acp +4    ✓

isaac-imessage declares a dependency on isaac.agent 0.1.0 while every other consumer requires 0.1.5+ (agent is now 0.1.6). The loader picks the highest so nothing breaks today, but the stale pin will bite whenever imessage actually relies on an old-agent behavior, and the warning is noise on every modules list.

## Desired outcome

- isaac-imessage's module dependency on isaac.agent reflects the version it is actually built/tested against (current agent line).
- `isaac modules list` on a root with imessage + acp + server shows no version conflict.

## Acceptance criteria

- [ ] isaac-imessage manifest/dependency declaration updated; `bb spec` / `bb features` green in isaac-imessage.
- [ ] A fresh `isaac modules list` (imessage + other agent consumers configured) reports zero version conflicts.

## Likely repo scope

isaac-imessage (dependency declaration); possibly a doc note in foundation module docs if the pin convention is unclear.


## Worker Notes (2026-07-02)
- Investigated in sibling repo `isaac-imessage`.
- The bean body's exact version numbers are stale, but the core issue remains: `isaac-imessage` was pinned behind the active agent line.
- Updated `isaac-imessage/deps.edn` to align both runtime and spec-time `isaac-agent` coords with the current agent head used in this workspace:
  - runtime `io.github.slagyr/isaac-agent` → `ee0b06294e37690ba6b2f36fdd8ea660ce6b94f7`
  - spec `io.github.slagyr/isaac-agent-spec` → same commit / spec root
- Verified in `isaac-imessage`:
  - `bb spec` → green
  - `bb features` → green with 3 pre-existing pending scenarios, 0 failures
- `bb lint` is not a defined task in this repo's `bb.edn`; not run.
- This satisfies the dependency-alignment part of the bean. The remaining acceptance check (`isaac modules list ... reports zero conflicts`) needs verification in a root that composes imessage with the other agent consumers after this module commit is consumed.
- Implementation commit in `isaac-imessage`: `1f080d8`.
