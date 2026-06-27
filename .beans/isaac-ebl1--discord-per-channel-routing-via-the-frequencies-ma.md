---
# isaac-ebl1
title: 'Discord: per-channel routing via the frequencies map'
status: todo
type: feature
priority: normal
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T17:32:55Z
parent: isaac-4e4b
blocked_by:
    - isaac-rqlc
---

CONFIRMED (2026-06-27): discord inbound routing IS frequencies-shaped. The :discord/channels config maps each channel id to {:crew :model :session :name} — selection (:session default 'discord-<channel-id>', :crew) + override (:model). It builds a charge from that, like cron/hooks.

Adopt frequencies: a channel's routing config becomes a frequencies map (validated by the shared schema), unifying discord's bespoke per-channel schema with the rest. Inbound message -> channel frequencies -> resolved session + override -> turn. Blocked-by the frequencies rename.

## Deploy
Migrate zanebot config/discord.edn :discord/channels {:crew :model :session} -> frequencies map to the frequencies shape before flipping the schema (one-time, ops). Strict validation will fail loud if missed.

## Scenarios (2026-06-27) — 2 wiring scenarios; regression net = existing routing.feature
discord channel config (comms.discord.discord/channels.<id>.{session,crew,model}) is already frequencies-shaped + tested in features/comm/discord/routing.feature. Adoption: unify the channel config to the shared frequencies schema (.model -> .with-model; gain .session-tags/.create/.prefer/.with-*) and route via the shared resolver instead of discord's own routing fn. Inbound: MESSAGE_CREATE -> channel frequencies -> resolved session + override -> turn.

### S1 — selection wiring: route by session-tags (new capability)
config comms.discord.discord/channels.C999.session-tags ["project/coil"]; session coil-wk tagged :project/coil; MESSAGE_CREATE on C999 -> lands on coil-wk via the shared resolver (discord couldn't tag-select before).

### S2 — override wiring: .with-model (renamed from .model)
channels.C999.{session=kitchen, with-model=grover2}; grover2 -> echo-alt; MESSAGE_CREATE -> kitchen turn runs on echo-alt.

Regression net: routing.feature (default discord-<channel-id> session, .session override, .crew, same-channel-same-session) stays green; .session/.crew fold into the flat frequencies map; .model -> .with-model. Scope: wiring only (per 4e4b). New steps: none (config:/sessions-exist/MESSAGE_CREATE/transcript reused); confirm the foundation 'isaac EDN file' fs-step loads in discord's harness for the model entity in S2.
