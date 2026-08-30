---
# isaac-9i5w
title: 'Discord gateway reconnect storm: single-flight, RESUME on opcode 7, cap retries'
status: in-progress
type: bug
priority: high
tags:
    - discord
    - gateway
created_at: 2026-08-30T15:13:13Z
updated_at: 2026-08-30T15:46:31Z
---

Likely repo: **isaac-discord**. Related: isaac-ceeq (double auth on the *same* reconnected socket's HELLO — fixed; this is a *second* reconnect task), isaac-wtg8 (uuid reconnect task ids so "task already scheduled" no longer throws — that let two tasks run).

## Observed (2026-08-29 / 2026-08-30, zanebot)

Discord emailed: ZaneBot connected >1000 times; they reset the token.

Logs:

- **2477** `:discord.gateway/identify` on 2026-08-29 (peak **72/min**, **989** same-second pairs).
- **448** more on 2026-08-30 until ~14:26Z, then stop (almost certainly **4004** `fatal-close?` after the reset).
- Opcode 7 → disconnect, then **two** `reconnect-attempt` + **two** IDENTIFY in the same millisecond.
- Reconnect attempts often go out as **IDENTIFY** even though opcode 7 asked for **RESUME**.

**Do not paste a new Discord token until this ships.** The storm will burn the next one the same way.

## Root cause (read in `gateway.clj` / `service.clj`)

1. **Dual schedule.** `schedule-reconnect!` mints a new uuid task id, cancels the *stored* id, then schedules. Two concurrent `on-close!` (opcode 7's `:mode :resume` plus the socket's 1000/1006) each store a different id; cancel misses the other; both fire. `ensure-recovery!` on the heartbeat path can schedule a second if `:reconnect-task-id` is still nil. `:reconnect-in-flight?` is only set on the no-scheduler path.
2. **Mode flip.** Opcode 7 calls `on-close!` with status 4000 `:mode :resume`. `transport-close!` then fires the WS `:on-close` (often 1000 — in `reidentify-close-codes`). The fake gateway's `close!` is a no-op, so specs never saw this. Last close wins → IDENTIFY. Two IDENTIFYs on one token → Discord invalidates the session → more opcode 7.
3. **Unlimited retry.** `:on-error :retry`, `retry-attempts Long/MAX_VALUE`, backoff 1s–30s. Failures never give up. 30s max is still 2 IDENTIFY/min = 2880/day, over Discord's ~1000/day IDENTIFY cap.
4. **Zombie client.** `service.registry/register!` `conj`s onto a set. `DiscordRegistration` equals by `identical?` comm-impl. A hot-reload that installs a *new* `DiscordIntegration` without `on-unload` of the old one leaves the old gateway reconnecting. Watchdog `force-registration-reconnect!` does `stop!` the current instance only.

ceeq's `:auth-sent?` guard is still correct for "HELLO must not re-IDENTIFY on *one* socket." It does not stop a second socket.

## Decisions (2026-08-30, Micah)

One bean. Ship the reconnect gate before installing a new token. Dropped the opcode-7-alone RESUME scenario (duplicate of gateway_spec). Kept: race → one RESUME; park after failures; stop cancels pending reconnect.

## Behavior

1. **Single-flight.** At most one reconnect in flight per client. Concurrent `on-close!` no-ops if a reconnect is already pending, unless the new close is fatal (4004 / ≥4010). Do not mint a fresh uuid per schedule — CAS a single pending task (stable id or `compare-and-set` on `:reconnect-task-id` / `:reconnect-in-flight?`).
2. **Opcode 7 → RESUME** when `session-id` is present. A racing 1000/1006 must not replace a scheduled RESUME. Discord opcode 7 means reconnect and resume.
3. **Cap.** Named constant `max-reconnect-attempts` = **8** (not `Long/MAX_VALUE`). Count `do-reconnect!` attempts in a disconnect episode. Reaching READY resets the counter. After 8 without READY (connect throws *or* socket opens then dies): log `:error :discord.gateway/reconnect-exhausted`, park (`:reconnect-exhausted?`), no further schedule. `ensure-recovery!` must honor the flag — do not un-park on the next heartbeat.
4. **Park recovery.** Existing watchdog (5 min stale → `stop!` + fresh `connect!`) is the un-park path. New client, counter reset. One IDENTIFY per 5 min if Discord stays down — under the daily cap. Do not shorten the watchdog to "fix" this.
5. **stop! wins.** `gateway/stop!` already cancels reconnect and sets `running?` false. Token remove / comm replace / watchdog force must `stop!` the *old* client. One live Discord gateway client per process: `register-comm!` of a new instance stops any other registered discord client's reconnect.

Backoff 1s–30s stays. Existing 4000 RESUME / 1006 IDENTIFY / 4004 fatal scenarios stay.

## Features (`isaac-discord`, `@wip`)

`features/comm/discord/reconnect.feature`

- :48 opcode 7 plus a racing close sends one RESUME
- :61 reconnect failures park instead of IDENTIFY-storming
- :71 stopping the client cancels a pending reconnect

Reuse: opcode 7, reconnect delay, `sends RESUME:`, `sends exactly one RESUME or IDENTIFY on reconnect`, log matching, `the Discord client is disconnected`, close-with-code.

New steps:

1. `Given the Discord Gateway fails subsequent connections` — `connect-ws!` throws after the Background handshake. Snapshots auth count.
2. `When the Discord client is stopped` — `gateway/stop!` on the active client. Snapshots auth count.
3. `Then the Discord client sends no further IDENTIFY or RESUME` — auth delta since last snapshot is 0.

Clock 180000 ms in :61 covers 8 attempts at 1s–30s (sum ≈ 121s). Keep that budget if the constant stays 8.

ceeq's `gateway.feature` "exactly one RESUME or IDENTIFY" stays; it is same-socket HELLO, not this race.

## Specs

`spec/isaac/comm/discord/gateway_spec.clj`

- Opcode 7 then a 1000 `on-close` before the delay fires → one op 6, zero extra op 2.
- `do-reconnect!` that opens then dies without READY, 8 times → `:discord.gateway/reconnect-exhausted`, no 9th auth. READY in between resets the counter.
- After exhaust, `check-liveness!` / `ensure-recovery!` does not schedule another reconnect.
- `stop!` with a pending reconnect task → handler does not fire; no further auth.

`spec/isaac/comm/discord/service_spec.clj`

- `register-comm!` of a second `DiscordIntegration` while the first has a pending reconnect `stop!`s the first (no second IDENTIFY loop). Registry may still hold one live client, not two running gateways.

Existing opcode 7 / 9 / 1006 / heartbeat-ack-timeout / dead-socket / watchdog specs stay green.

## Acceptance

```
cd isaac-discord
bb spec spec/isaac/comm/discord/gateway_spec.clj spec/isaac/comm/discord/service_spec.clj
bb features features/comm/discord/reconnect.feature:48
bb features features/comm/discord/reconnect.feature:61
bb features features/comm/discord/reconnect.feature:71
```

Remove `@wip` from those three scenarios when green. Existing reconnect / gateway / lifecycle / service_lifecycle scenarios stay green.

## Out of scope

- Installing a new Discord bot token on zanebot.
- Changing heartbeat interval, intents, or REST send.
- Raising or lowering the 5 min watchdog (it is the post-park recovery).
- Reverting ceeq's `:auth-sent?` HELLO guard.
