---
# isaac-udnm
title: 'Unauthenticated burst control: per-client detection, one attention post per burst, optional 429 throttle (:server :burst)'
status: in-progress
type: feature
priority: normal
tags:
    - server
    - attention
    - security
created_at: 2026-09-11T03:49:12Z
updated_at: 2026-09-11T05:31:57Z
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

## Acceptance

Scenarios: `isaac-server/features/server/burst.feature` (7, @wip, commit
8d64927): threshold edge + one post; burst continues → no second post;
per-client window; cooldown ends the burst with a total (foundation
`the clock is fixed at` drives the window — the burst counter must read
the foundation clock, not System/currentTimeMillis); throttle 429 before
auth for a flagged client while another client with a valid token gets
200; loopback never throttled; `config schema server.burst` lists the knobs.

New steps (isaac-server steps, not foundation):
- `the client sends {method} {path} with header {h} {n} times`
- `the client sends {method} {path} {n} times with headers:` (table)
The last response of a counted send is what `the response status is …`
inspects. Attention goes through the isaac-agent notifier (already a
dependency); new log events `:server/burst-detected`, `:server/burst-ended`,
`:server/burst-throttled`.

```
cd isaac-server
bb features features/server/burst.feature features/server/logging.feature
bb spec spec/isaac/server
bb ci
```

All seven pass with @wip removed; bb ci green. Deploy: server bump, then
enable on zanebot with `:server {:burst {:threshold 30 :window-ms 60000
:cooldown-ms 600000}}` (notify on, throttle off).

Live sample (planner, 2026-09-11 05:15–05:16Z): client 8.235.2.103 hit GET / on zanebot:6674 ~70 times in ~75 s, every one answered 401 (`:server/response-sent :status 401`), 278 such lines in the last 3000 log lines. Auth held; the noise is the only cost. Use as the fixture shape for the burst detector.
