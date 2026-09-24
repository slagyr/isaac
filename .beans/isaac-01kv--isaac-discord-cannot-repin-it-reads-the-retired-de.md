---
# isaac-01kv
title: 'isaac-discord cannot repin: it reads the retired [:defaults :crew] in production source'
status: todo
type: bug
priority: high
created_at: 2026-09-24T21:29:59Z
updated_at: 2026-09-24T21:29:59Z
---

Repo: **isaac-discord**.

## Problem

isaac-ruom (foundation `97da637` + its agent half `28404cb`) retired the flat
`:defaults` keys as validation errors:

| retired | replacement |
|---|---|
| `:defaults :crew "main"` | `:defaults :frequencies :crew "main"` |
| `:defaults :model "grover"` | `:defaults :crew :model "grover"` |

Every other module has migrated — isaac-http under isaac-0r95, isaac-gchat and
isaac-gmail likewise, isaac-imessage under isaac-v2x1. **isaac-discord has
not**, and unlike the others it cannot be fixed by touching fixtures alone:

- `src/isaac/comm/discord.clj:144` reads `(get-in cfg [:defaults :crew])` — a
  **production source** read of a retired key
- fixtures at `spec/isaac/comm/discord_spec.clj:322,343` and
  `spec/isaac/comm/discord/discord_steps.clj:374` write the old shape
- the app-spec fixtures write configs with **no `:defaults` at all**, which now
  fail `defaults.frequencies.crew is required`

Measured: with the pins bumped to the fleet set (foundation `9ab2527`,
isaac-agent `b6eb475`, isaac-http `689d368`), `bb ci` exits 1 —
`105 examples, 1 failure` at `spec/isaac/server/discord_app_spec.clj:112`
("connects Discord gateway when token is added via config hot-reload"), with
features never reached.

## Why it matters

Because discord cannot repin, it cannot receive **isaac-clba**'s delivery audit
logging (the `:error` and real `:target` on failed deliveries), and it will
fall further behind with every foundation change. It is now the only comm
module left on the old shape.

The source read is the sharp part: a config key retired in one repo is still
being read by another in production, and only the suite is noticing.

## Acceptance

- `src/isaac/comm/discord.clj` reads the default crew from its current
  location, not `[:defaults :crew]`. Check for any other retired-key reads in
  src while there.
- Fixtures and steps write the new `:defaults` shape; fixtures that omit
  `:defaults` entirely supply what is now required.
- Pins move to the fleet set as a coherent group (foundation, isaac-agent,
  isaac-http) — see isaac-57rl.
- `bb ci` green.

## Notes

isaac-gchat and isaac-gmail are already past this; each needs only a one-line
isaac-agent repin (`da9214a` → `b6eb475`) to pick up isaac-clba. Verified in a
scratch run: gchat `bb ci` exit 0 (173/0, 54/0), gmail exit 0 (127/0, 40/0).
That is a separate, trivial change — do it whenever, it is not blocked on this.
