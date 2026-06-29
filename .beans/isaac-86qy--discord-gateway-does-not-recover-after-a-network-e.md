---
# isaac-86qy
title: Discord gateway does not recover after a network-error disconnect (bot offline until restart)
status: todo
type: bug
priority: high
created_at: 2026-06-29T15:11:00Z
updated_at: 2026-06-29T15:11:00Z
---

On zanebot the discord bot went offline at 2026-06-27T22:06:06 (network error IOException "Can't assign requested address" -> :discord.gateway/disconnected :reason "closed", status nil) and NEVER reconnected — last successful :discord.gateway/hello/ready was 16:26, and there are no connect attempts that succeeded after 22:06 for ~1.7 days (until restart). The bot was effectively DOWN the whole time.

## Diagnosis (gateway.clj on-close!)
- A non-fatal close (status nil from the IOException) falls through on-close!'s else branch -> (reconnect! client :identify). So reconnect IS attempted.
- But against a persistent network failure the reconnect attempt fails and the gateway does NOT keep retrying — it gives up / single-shots, leaving the gateway disconnected indefinitely. (Confirm reconnect! retry/backoff behavior.)
- Side effect: reconnect never reaches schedule-heartbeats!, so the old heartbeat is never cancelled -> the isaac-gkx9 "Output closed" loop (3581x). gkx9 stops the error spam; THIS bean restores the bot.

## Impact
Higher than gkx9: any transient network blip on zanebot can take discord offline until a manual restart.

## Fix
Reconnect with backoff that KEEPS RETRYING until the gateway re-establishes (never give up on transient/network errors), like isaac-9rdk did for the ACP proxy. Distinguish terminal/fatal closes (4004/>=4010, don't retry) from transient network errors (retry forever with capped backoff).

## Related
isaac-gkx9 (heartbeat leak symptom), isaac-dcr1 (reconnect after 1006), isaac-9rdk (ACP proxy never-give-up reconnect — the model).
