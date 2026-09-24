---
# isaac-01kv
title: 'isaac-discord cannot repin: it reads the retired [:defaults :crew] in production source'
status: completed
type: bug
priority: high
created_at: 2026-09-24T21:29:59Z
updated_at: 2026-09-24T21:54:18Z
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

## Landed 2026-09-24 as isaac-discord `6df59f7`

`bb ci` exit 0 under `ISAAC_GIT=1` (so the pins are honoured rather than the
sibling checkouts): 55/0 native, 108/0 jvm-spec, 68/0 features (3 pending).

**One correction to this bean's premise.** The `discord_app_spec.clj:112`
failure was *not* caused by the repin — it was already failing at the old pins
(`105 examples, 1 failure`, measured). Foundation has required a default crew
since isaac-bfwn, so that app-spec's `:defaults`-less on-disk config was
already invalid. What the repin actually added was **34 feature failures**,
which `bb ci` never reported because jvm-spec exits first. Both are fixed.

Only one production read existed (`discord.clj:144`), confirmed by a sweep of
every `get-in` / `:defaults` / `:crew` / `:model` in `src/`.

## A collision worth recording: isaac-zule landed in the same function

While this was in flight, `f9f746d` (isaac-zule, "no crew named main") changed
the same `channel-crew-id` — removing the `"main"` literal so a channel with no
crew resolves to nil and the drive applies `defaults.crew`. The bean branch was
cut before it, so a naive rebase would have **reinstated the `"main"` literal**
that commit deliberately removed. Resolved keeping isaac-zule's intent: no
literal, only the read relocated to `isaac.config.defaults`.

Rebasing then failed a spec **isaac-zule itself had added**, asserting
`{:defaults {:crew "yopp"}}` resolves to `"yopp"` — the retired flat shape. It
passed only because the code under test still read the retired key; the moment
the read moved, it returned nil. Migrated to
`{:defaults {:frequencies {:crew "yopp"}}}`.

That is isaac-57rl's thesis in miniature: a retired key stays invisible while
one stale reader keeps it alive, and the test that should catch it is written
in the same stale shape. Worth remembering that the fixture and the code can
be wrong *together* and still be green.
