---
# isaac-ebl1
title: 'Discord: per-channel routing via the frequencies map'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T18:30:00Z
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


## Implementation (work-2)
Repo: isaac-discord @ 966f52b

- Inbound routing builds channel frequencies and resolves via `isaac.session.frequencies/resolve-session-targets`.
- `:discord/channels` manifest schema uses frequencies keys (`:session`, `:session-tags`, `:with-model`, …); `:model` → `:with-model`.
- New `features/comm/discord/frequencies.feature` (session-tags + with-model wiring).
- `routing.feature` regression green with `with-model`; deps bump isaac-agent `10093b4`, isaac-server `eb51cc4`.

Verification: `clojure -M:dev-local:spec` (66 examples, 0 failures); features `routing.feature` + `frequencies.feature` (11 examples, 0 failures).

## Verification failed (2026-06-27)
The Discord implementation is present on fetched `isaac-discord` `966f52b`, and the focused spec slice is green:

- `env ISAAC_GIT=1 bb spec spec/isaac/comm/discord_spec.clj` -> `66 examples, 0 failures, 132 assertions`

But the approved feature surface does not run on the pinned cross-repo heads named in the bean. I created a real sibling worktree set at:

- `isaac-discord` `966f52b`
- `isaac-agent` `10093b4`
- `isaac-foundation` `6e81f78`
- `isaac-server` `eb51cc4`

and reran:

- `bb features features/comm/discord/routing.feature features/comm/discord/frequencies.feature`

That still fails before scenarios load:

`Could not locate isaac/foundation/harness_config_steps__init.class ...`

The failure comes from `isaac.session.session-steps` requiring `isaac.foundation.harness-config-steps`, which is not present on the pinned foundation head. So this is not a verifier-layout miss; the current pinned feature harness is not green.

## Fix (work-2, 2026-06-27)
Repo: isaac-discord @ a5c1f79

- Bumped all `isaac-foundation` pins from `6e81f780` → `f9be40b` (includes `harness_config_steps.clj` in spec-support).
- Added `isaac-foundation-test-support` to `:spec` alias (features already had it at the old SHA).

Verification with `ISAAC_GIT=1` on pinned heads (discord `a5c1f79`, agent `10093b4`, foundation `f9be40b`, server `eb51cc4`):

- `bb spec spec/isaac/comm/discord_spec.clj` → 66 examples, 0 failures
- `bb features features/comm/discord/routing.feature features/comm/discord/frequencies.feature` → 11 examples, 0 failures
