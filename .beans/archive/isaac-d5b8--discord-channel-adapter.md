---
# isaac-d5b8
title: "Discord channel adapter"
status: completed
type: feature
priority: normal
created_at: 2026-04-20T23:07:03Z
updated_at: 2026-04-22T18:33:07Z
---

## Description

Epic: enable Isaac crew to interact over Discord. Replaces the primary surface of zanebot (see isaac-0jy analysis).

Architecture: inline under src/isaac/channel/discord/ with an intentional namespace boundary. isaac.channel.discord.gateway owns the WSS event pump + REST send — no Isaac concerns. isaac.channel.discord.channel implements the isaac.channel protocol and bridges gateway events into Isaac's session/crew model. If the gateway code proves reusable later, extract to a standalone library.

Runtime: the Discord bot runs inside bb isaac server. Must work under both bb and clj (see isaac-dps dual-runtime requirement). discljord is out — transitively pulls clojure.data.json which uses reflection not available in bb's native image. Hand-rolled on http-kit (already a dep), using JDA/discljord as references, not runtime.

v1 scope:
- Channel messages only (no DMs, no mentions required — zane's discord usage is mostly 1:1)
- Bot token + allow-from filter (guild ids + user ids) in config
- Session per (user, channel) using the existing agent:<id>:discord:<channel>:<user> key shape
- Inline replies (same channel), no threading
- Long-message splitting at Discord's 2000-char cap
- Typing indicator while the crew is generating
- Reconnect on Gateway disconnect

Config shape (proposed):
  :channels {:discord {:token      "${DISCORD_TOKEN}"
                       :crew       :main
                       :allow-from {:users  ["123456"]
                                    :guilds ["789012"]}}}

Decomposed into sub-beads — see dependencies. This bead tracks the epic; individual sub-beads carry the acceptance criteria.

