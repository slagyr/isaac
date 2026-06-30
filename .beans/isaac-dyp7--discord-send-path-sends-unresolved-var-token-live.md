---
# isaac-dyp7
title: Discord send path sends unresolved ${VAR} token (live config bypasses secret resolution) -> REST 401 dead-letters
status: in-progress
type: bug
priority: high
tags:
    - unverified
    - discord
    - comm
created_at: 2026-06-30T19:59:36Z
updated_at: 2026-06-30T20:23:50Z
---

## Symptom (production — discord sends 401 / dead-letter)

`comm_send {:comm "discord"}` permanently dead-letters even though discord is active
and the channel resolves. Live proof (after the 0.1.16 deploy):

```
:event :discord.reply/http-error, :channelId "1471612995535900712", :status 401,
  :bodyPreview "{\"message\": \"401: Unauthorized\", \"code\": 0}"   (rest.clj:50)
:event :delivery/dead-lettered, :reason :permanent, :id "9d91"     (2026-06-30T19:25:47Z)
```

`"pub"` resolved correctly to channel `1471612995535900712`; the gateway is connected
and heartbeat-ack'ing. Only the REST auth fails.

## Root cause: the live send path sends the UNRESOLVED `${VAR}` token

`discord/token` is stored as a `${VAR}` secret reference, resolved from `<root>/.env`
by `resolve-env-values` (foundation config/cli/common.clj:193, applied at load,
common.clj:251). Two paths read the token differently:

- **Gateway** authenticates once at boot via the full config load → `resolve-env-values`
  runs → real token → connects and stays up.
- **Send path** (introduced by `vhyw`) reads the token **live per send**:
  `send!` → `live-discord-cfg` → `runtime-discord-cfg state-dir` (discord.clj:311–316),
  which merges `effective-config` + `discord-slice-from-root` **without** running
  `resolve-env-values`. So it passes the raw `"${VAR}"` straight into
  `(str "Bot " token)` → Discord returns **401**.

Net: gateway works, every REST send 401s. `vhyw` ("read live config so channel-name
mappings hot-reload without restart") dragged the **token** into the unresolved live read.

## Evidence

- `rest.clj:45` builds the header correctly: `"Authorization" (str "Bot " token)`.
- The configured `discord/token` value is **20 chars, 0 dots, starts with `$`** — i.e. a
  `${...}` reference (e.g. `${DISCORD_BOT_TOKEN}`); a real bot token is ~70 chars / 2 dots.
- `resolve-env-values` is applied in the load path, **not** in `runtime-discord-cfg`.

## Fix

`runtime-discord-cfg` (the live read) must resolve `${VAR}` references the same way the
boot/load path does — run `resolve-env-values` over the merged slice — **or** the send
path should reuse the gateway/boot-resolved token instead of re-reading raw config. Only
the channel-name map needs live reloading; the token must end up resolved.

## Acceptance (gherkin — @wip, written)

`isaac-discord features/comm/discord/comm_send_token.feature` — a `${VAR}` token resolves
to the real secret on `send!` (outbound `Authorization` = `Bot <real-secret>`). Reuses the
existing send/target/HTTP-assert steps + the foundation `.env` file step — **no new steps**.
Today this fails (sends `Bot ${...}`); after the fix it passes.

## Related

- isaac-vhyw — introduced the live-config send path (the regression).
- isaac-3ldm — the compile fix that got discord active enough to reach this 401.


## Update (architecture correction)

Config ${VAR} resolution is a FOUNDATION concern, not discord's. Therefore:
- The fix is NOT to resolve env vars inside discord — it is to make discord's live read go THROUGH foundation's resolving config loader instead of raw edn/read-string (effective-config / discord-slice-from-root / runtime-discord-cfg). Only the channel map needs live reload; the token must arrive already resolved by foundation.
- The earlier @wip scenario (features/comm/discord/comm_send_token.feature) was REMOVED — testing foundation's resolution through discord's send path is the wrong level; that behavior belongs to foundation.
- This bug is one instance of a class now tracked by isaac-q6et (audit all projects + worker rule + validator check against config-read bypass).
