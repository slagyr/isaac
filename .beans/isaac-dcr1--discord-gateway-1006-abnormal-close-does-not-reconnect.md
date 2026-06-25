---
# isaac-dcr1
title: 'Discord gateway: 1006 abnormal close does not trigger reconnect (leaves comm unresponsive)'
status: in-progress
type: bug
priority: normal
tags:
    - discord
    - comm
    - gateway
    - hot-reload
    - unverified
created_at: 2026-06-25T12:00:00Z
updated_at: 2026-06-25T18:15:01Z
---

## Context

On zanebot the discord comm connects after server boot / config snapshot, does a few heartbeats, then gets a 1006 close and stays down (no more `ready` / heartbeats / events). The bot becomes unresponsive on Discord.

Observed right after a server boot + heavy marvin/gpt-5.4 activity + ACP session prompt, followed ~15s later by a separate `prompt-cli` config load that caused a wave of berth registrations.

## Reproduction (zanebot logs)

```
17:14:06.447506Z  config/set-snapshot  "server boot"
17:14:07.742396Z  discord.gateway/connected
17:14:07.950921Z  discord.gateway/ready
... heartbeats ...
17:16:52.932687Z  discord.gateway/heartbeat-ack
17:17:17.288517Z  discord.gateway/disconnected  {:status-code 1006, :reason ""}
17:17:32.472518Z  config/set-snapshot  "prompt-cli"
17:17:33.xxxxx   berth/registered (many :isaac.agent/tools + slash-commands)
... no further discord.gateway/ready or connected ...
```

No `:discord.gateway/error` or transport error immediately preceding the close. Later hot-reload at 17:45 also seen but too late.

## Root cause (from gateway.clj)

```clojure
(defn- fatal-close? [status]
  (or (= 4004 status) (and (some? status) (>= status 4010))))

(defn- on-close! [client payload]
  ...
  (cond
    (fatal-close? status)  (swap! state assoc :running? false) ...
    (resumable-close-codes status)  (reconnect! :resume)
    (reidentify-close-codes status) (reconnect! :identify)
    :else
    (swap! state assoc :running? false)   ; 1006 lands here
    (log/info :discord.gateway/disconnected ...)))
```

- 1006 is neither fatal nor in the resumable (#{4000 4001 4002 4003 4008}) or reidentify (#{1000 1001 4007 4009}) sets.
- `running?` goes false → reader loop exits, no reconnect.
- The comm service (service.clj) only calls `connect-registration!` on `start`, `on-register!`, or token-add paths; a post-boot 1006 does not go through those.

1006 ("abnormal closure") is common for network blips, abrupt closes, load-induced drops, or WS library transport loss. The client should treat most non-fatal 1000/1006-ish closes as "try identify again".

## Acceptance

- After a 1006 (or similar non-fatal) gateway close, the discord client automatically attempts reconnect (preferably :identify, falling back to resume if session-id still valid).
- `running?` stays true until a truly fatal close (4004 etc.).
- Reconnect restores `ready` and message delivery without requiring a full server restart or manual `register-comm!`.
- Existing gateway tests (including the 4004 fatal case) stay green.
- Hot-reload / prompt-cli / ACP config loads do not leave the gateway in a permanently stopped state.

## Notes / open questions

- Should 1006 always be reidentify, or only when we don't have a valid session-id?
- Does the comm service need an explicit "ensure-connected" hook on config hot-reload or after berth activation?
- Related to earlier stale-comm and hot-reload-not-immediate issues (isaac-eb1t, isaac-hrl1, isaac-m4bi family).
- The 17:17 prompt-cli registrations were from a *separate* CLI process (not the server); they are a red herring for the disconnect itself but highlight that config loads are happening frequently.

## Handoff

Add handling in `on-close!` (and/or the reader loop when it sees nil/timeout) so 1006 triggers `reconnect! :identify`. Verify with a test that injects a 1006 close and asserts a subsequent identify + ready.
