---
# isaac-h65j
title: "Discord routing: add :comms.discord.{crew,model} default tier and channel :name"
status: completed
type: bug
priority: normal
created_at: 2026-05-01T16:14:37Z
updated_at: 2026-05-05T22:28:45Z
---

## Description

## Background

isaac-3qzm landed channel-based Discord routing with two-tier crew/model
resolution:

  1. `:comms.discord.channels.<chan-id>.{crew,model}` — per-channel
  2. `:defaults.{crew,model}`                         — global

Two gaps to close.

## Gap 1: missing Discord-wide tier (regression)

The prior code read `(:crew (discord-config cfg))` — i.e.
`:comms.discord.crew` was the per-Discord default. The new
implementation drops that tier, so any user with a `:comms.discord.crew`
setting silently falls through to `:defaults.crew`.

Concretely: zane's `~/.isaac/config/isaac.edn` has
`:comms.discord.crew "marvin"`. Pre-3qzm, Discord turns ran as marvin.
Post-3qzm, they fall through to `:defaults.crew :main`.

Fix: insert the middle tier so resolution becomes:
  1. `:comms.discord.channels.<chan-id>.{crew,model}`
  2. `:comms.discord.{crew,model}`
  3. `:defaults.{crew,model}`

This matches the spec already captured in
features/comm/discord/routing.feature (note the @wip scenario covering
this case in the version we drafted — Micah's committed routing.feature
omits it).

## Gap 2: add :comms.discord.channels.<id>.name

A channel id like `555020` is opaque. Knowing the human name
(`#cooking`, `#bot-test`) gives the bot useful context — it shows up
in the conversation, in any cross-channel reasoning, and matches the
`channel_label` field in the openclaw-style untrusted user prefix.

Schema addition:
  ```
  :comms {:discord {:channels {\"555020\" {:name    \"cooking\"
                                            :session \"kitchen\"
                                            :crew    \"sous-chef\"}}}}
  ```

Wire `:name` into the untrusted user-message prefix as `channel_label`
(Discord doesn't supply a name in MESSAGE_CREATE; this is the cleanest
way to get it without REST lookups). When `:name` is absent, omit
`channel_label` rather than substituting the raw id.

## Acceptance

- A new scenario in routing.feature asserts `:comms.discord.crew`
  applies when there's no per-channel override (the bender scenario
  from the original spec draft).
- A new scenario in turn_context.feature asserts `channel_label`
  appears in the untrusted prefix when `:comms.discord.channels.<id>.name`
  is set, and is absent when it isn't.
- Existing isaac-3qzm scenarios continue to pass.

## Out of scope

- Auto-discovery of channel names via Discord REST. Config-only for
  now; a future bead can layer auto-discovery if it becomes painful.

