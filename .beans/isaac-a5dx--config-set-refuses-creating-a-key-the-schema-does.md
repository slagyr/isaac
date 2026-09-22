---
# isaac-a5dx
title: config set refuses creating a key the schema does not declare inside a schema'd map (policy split from isaac-cgxa)
status: todo
type: feature
priority: normal
tags:
    - foundation
    - config
created_at: 2026-09-22T22:45:06Z
updated_at: 2026-09-22T22:45:06Z
---

## Problem

`isaac config set comms.gchat.gchat.allow-from …` (the pre-cgxa mis-parse) wrote
`{:comms {:gchat {:gchat {:allow-from …}}}}` — a key the composed schema does
not know, inside a map it does know — and exited 0. isaac-nq4c made unknown
keys warn on load instead of vanishing; isaac-fun8 made value-validator errors
refuse a set. The gap left: a set that would CREATE an unknown key under a
schema'd map still writes and warns later, so a typo in a path lands in config.

Split out of isaac-cgxa (2026-09-22): that bean fixed the grammar (namespaced
segments stay whole); this is the policy.

## Decision to make (Micah)

Refuse, not warn: `config set` and `config unset` on a path whose parent is a
schema'd map refuse a segment the schema does not declare (static or a berth's
dynamic `:extra-schema`), with the same exit/message shape as a validator
refusal (fun8), listing the known keys at that level. `--force` (isaac-9mkp)
still writes. Open maps (`:key-spec`/`:value-spec` entity tables) accept any
key, as today.

## Scenarios (foundation features/cli, Marigold)

- set of an undeclared key under a schema'd map is refused; nothing written;
  the message names the parent path and the known keys.
- set of an undeclared key under an entity table (key-spec) still writes.
- `--force` writes the undeclared key and the load-time warning fires.
- unset of an undeclared key under a schema'd map is refused the same way.

## Acceptance

`bb spec`, the new feature, `bb ci` green in isaac-foundation; agent's
config-set scenarios (fun8) still green against the new foundation sha.

## Decision (Micah, 2026-09-22)

Refuse by default; `--force` writes the undeclared key and the load-time warning still fires. "A force lets you get away with it." Ready for a worker; scenarios above stand.
