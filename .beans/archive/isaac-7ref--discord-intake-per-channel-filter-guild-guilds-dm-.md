---
# isaac-7ref
title: "Discord intake: per-channel filter (guild→guilds, DM→users) with rejected-log"
status: completed
type: bug
priority: high
created_at: 2026-04-30T23:11:54Z
updated_at: 2026-05-01T18:40:01Z
---

## Description

Discord MESSAGE_CREATE intake currently AND-joins :allow-from.users and :allow-from.guilds at gateway.clj:116-118, then silently drops anything that fails. Two real consequences:

1. DMs always drop. A DM has no :guild_id, so (contains? guilds nil) is false even when the author is on the user allowlist.
2. Misconfiguration is invisible. When :allow-from.users is unset, it normalizes to #{} and every guild post is dropped with no log entry.

Fix: split the predicate by message kind, and log every drop at debug.

Semantics (spec'd in features/comm/discord/intake_filtering.feature):
- Guild post (has :guild_id):  accepted iff guild_id in :allow-from.guilds.
- DM (no :guild_id):           accepted iff author.id in :allow-from.users.
- Bot's own message:           always dropped.
- Drops log :discord.gateway/message-rejected at :debug with {:reason :guild | :user | :self, :authorId, :guildId, :channelId}.

Files likely touched:
- src/isaac/comm/discord/gateway.clj   (handle-dispatch! MESSAGE_CREATE branch)
- features/comm/discord/intake_filtering.feature   (new spec — remove @wip)
- features/comm/discord/intake.feature              (already updated to drop the obsolete user-AND-guild scenario in commit e7f1d96)
- spec/isaac/comm/discord/gateway_spec.clj          (cover the three drop reasons + DM accept path)

DESIGN
Spec: features/comm/discord/intake_filtering.feature

Run targeted scenarios:
  bb features features/comm/discord/intake_filtering.feature
  bb features features/comm/discord/intake.feature

ACCEPTANCE CRITERIA
- All scenarios in features/comm/discord/intake_filtering.feature pass with @wip removed.
- features/comm/discord/intake.feature still passes (note: one scenario was removed in e7f1d96 — the DM-vs-guild filter split makes user-AND-guild semantics no longer correct).
- Existing features/comm/discord/{gateway,reconnect,lifecycle,routing,reply,splitting,typing,idle}.feature still pass.
- bd doctor and bb spec are clean.

## Notes

Verification incomplete: automated behavior and tests passed, but the bead explicitly requires 'bd doctor' to be clean. Running 'bd doctor' in this embedded review environment returns 'not yet supported in embedded mode', so that acceptance criterion could not be verified here. Reopening rather than closing without that evidence.

