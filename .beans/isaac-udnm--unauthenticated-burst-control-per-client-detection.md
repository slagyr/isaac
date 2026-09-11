---
# isaac-udnm
title: 'Unauthenticated burst control: per-client detection, one attention post per burst, optional 429 throttle (:server :burst)'
status: draft
type: feature
priority: normal
tags:
    - server
    - attention
    - security
created_at: 2026-09-11T03:49:12Z
updated_at: 2026-09-11T03:49:12Z
---

Repo: **isaac-server** (`src/isaac/server/http.clj` — `wrap-auth` / `wrap-logging`
already see every request and, since 0.1.14, its `:client`), attention via the
isaac-agent notifier the server already depends on; `:server` config schema.

## Why

2026-09-11 02:39–02:49Z: a vulnerability scanner (185.177.72.66) sent ~600
requests in ten minutes through Tailscale Funnel — WordPress, `.env`, F5 tmui,
webmin, `/proc/self/environ`. Every one got a 401 in ~1 ms; nothing was
served. Micah found it by reading the log. Same class of gap as isaac-9xtv:
the door held, nobody was told. Narrowing Funnel by path was tried and
rejected (Funnel is per port; the whole server stays public on 443), so the
control lives in the server.

## Decisions (2026-09-11, Micah)

1. **Detect per client.** A sliding window of unauthenticated (401) responses
   per `:client`, counted in the auth middleware. Over `threshold` within
   `window-ms` → `:server/burst-detected` (client, count, window, sample of
   up to 5 paths). When the client has been quiet for `cooldown-ms` →
   `:server/burst-ended` (client, total, duration).
2. **Notify, once per burst.** On detection, one attention post through the
   existing notifier (`:attention :notify` coords): "Unauthenticated burst from
   <client>: <n> requests in <window>, paths like <a>, <b>, <c>." A second
   short post on burst end with the total and duration. Nothing in between,
   however long the burst runs; a return after cooldown is a new burst.
   **Default on.**
3. **Throttle, optional, default off.** When on, a flagged client gets a bare
   429 (no body, no WWW-Authenticate) before the auth check for the cooldown,
   and a single `:server/burst-throttled` count instead of per-request log
   lines. Loopback and tailnet (100.64.0.0/10) clients are never throttled —
   no self-lockout for the CLI or the MCP bridge.
4. **Config group `:server {:burst …}`**, schema'd so `isaac config schema
   server.burst` lists it:
   ```clojure
   :server {:burst {:threshold   30      ; unauthenticated responses
                    :window-ms   60000
                    :cooldown-ms 600000
                    :notify?     true
                    :throttle?   false}}
   ```
   Absent group = feature off. Hot-reloads like the rest of `:server`.
5. **Memory:** per-client window state is a bounded in-memory map (count +
   timestamps per client, evicted after cooldown); no transcript, no disk.

## Non-goals

Allow/deny lists by address; rate limits on authenticated traffic; anything
at the Tailscale layer; the `:client` field itself (shipped, 0.1.14).

## Acceptance (scenarios to plant, one at a time)

`features/server/burst.feature`:
1. 30 unauthenticated requests from one client inside the window → one
   `:server/burst-detected` and exactly one attention file in
   `comm/delivery/pending` naming the client and a path.
2. 60 more from the same client → still one pending file, no second post.
3. 29 from one client and 29 from another → no detection (per-client).
4. Burst then `cooldown-ms` of quiet (test clock) → `:server/burst-ended`
   with the total and a second pending file.
5. `throttle? true`: the 31st request answers 429 with no body before auth;
   a valid token from a *different* client still gets 200.
6. `throttle? true`: a loopback client over the threshold still gets 401,
   never 429.
7. `config schema server.burst` lists threshold, window-ms, cooldown-ms,
   notify?, throttle?.

```
cd isaac-server
bb features features/server/burst.feature features/server/logging.feature
bb spec spec/isaac/server
bb ci
```
