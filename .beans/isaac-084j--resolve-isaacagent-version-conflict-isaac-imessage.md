---
# isaac-084j
title: 'Resolve isaac.agent version conflict: isaac-imessage pins agent 0.1.0'
status: in-progress
type: task
priority: normal
created_at: 2026-07-02T22:05:29Z
updated_at: 2026-07-02T22:06:18Z
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
