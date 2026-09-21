---
# isaac-deds
title: 'One name for the comm berth: :isaac.agent/comm, no fallbacks, wrong name is an error'
status: todo
type: bug
priority: high
tags:
    - comm
    - config
created_at: 2026-09-21T04:25:21Z
updated_at: 2026-09-21T04:25:21Z
---

The comm berth has three names in circulation. Every reader accepts a different
pair, and **no reader sees all four comms**.

## Who declares what

| module | manifest key |
| --- | --- |
| isaac-discord | `:isaac.agent/comm` **and** `:isaac.http/comm` (both) |
| isaac-imessage | `:isaac.server/comm` |
| isaac-gmail | `:isaac.http/comm` |
| isaac-gchat | `:isaac.http/comm` |

## Who reads what

| reader | primary | fallback | blind to |
| --- | --- | --- | --- |
| `isaac.comm.factory/manifest-comm-contribution` (foundation :29) | `:isaac.server/comm` | `:isaac.agent/comm` | gchat, gmail |
| `isaac.config.comm-kinds/comm-kinds` (foundation :14) | `:isaac.server/comm` | `:isaac.agent/comm` | gchat, gmail |
| `isaac.config.checks` (agent :20, :53, :82) | `:isaac.http/comm` | `:isaac.agent/comm` | imessage |
| `isaac.tool.comm-send` (agent :16) | `:isaac.http/comm` | **none** | imessage |

Consequences already visible:

- the comm **factory** cannot find gchat's or gmail's contribution — discord only
  survives it by declaring two keys
- `comm-reserved-schema-errors` (the check that refuses `:type` as a comm field)
  never runs for imessage
- `comm-kinds`' primary lookup matches nothing any active comm declares; it
  survives on its fallback alone

## Decision (Micah, 2026-09-21)

**`:isaac.agent/comm` is the name.** isaac-agent owns `isaac.comm.protocol/Comm`
— the interface every comm implements — and comm callbacks are emitted by the
turn pipeline (`isaac.drive.turn`). That is agent territory.

`:isaac.http/comm` names the berth after one implementation's transport. HTTP is
not the door for comms generally: Discord is a websocket gateway, iMessage is
local BlueBubbles, ACP is stdio, the memory comm has no door. Only the Pub/Sub
push comms (gchat, gmail) arrive over HTTP.

**One name. No fallbacks.** A manifest declaring a comm under any other key is an
**error**, named and refused at load — not silently skipped, and not quietly
accepted by whichever reader happens to match.

## Work

- rename every declaration to `:isaac.agent/comm` (discord drops its duplicate)
- isaac-agent declares the berth, since it owns the protocol
- every reader looks up exactly one key; delete all `or` fallbacks
- an unknown `:isaac.*/comm` key in a manifest is a load error naming the module
  and the key
- fix the factory docstring, which still says ":isaac.server/comm config berth"

## Acceptance

- all four comms are visible to the factory, comm-kinds, checks and comm-send
- a manifest declaring `:isaac.http/comm` or `:isaac.server/comm` fails to load
  with an error naming the module and the offending key
- `comm-reserved-schema-errors` runs for every comm, imessage included
- no `or` fallback on a berth key remains in either repo
