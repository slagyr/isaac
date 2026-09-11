---
# isaac-udnm
title: 'Unauthenticated burst control: per-client detection, one attention post per burst, optional 429 throttle (:server :burst)'
status: completed
type: feature
priority: normal
tags:
    - server
    - attention
    - security
created_at: 2026-09-11T03:49:12Z
updated_at: 2026-09-11T06:39:59Z
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


## Handoff (scrapper@isaac-work-1)

branch: bean/isaac-udnm @ 6e2e753 (base origin/main@d49972f)

Implemented per-client unauthenticated burst control in isaac-server:
- `src/isaac/server/burst.clj` — sliding window, detect/end/throttle, attention enqueue
- `wrap-burst` before `wrap-logging`/`wrap-auth`; 401s counted in wrap-auth
- schema `:server :burst` knobs; absent group = off
- @wip removed from `features/server/burst.feature` (7 scenarios)

Verified: `bb spec spec/isaac/server` green (118/0); burst+logging features 10/0.



## Verify fail (attempt 1, 2026-09-11): burst.feature attention pending-file assertions red (Expected 1 got 0)

HEAD: 6e2e753b800c3a98f95ab59387d85ef0713be931 (bean/isaac-udnm, base origin/main@d49972f)
Working tree: dirty: untracked verify-full-features.log (2026-09-03 leftover, not this bean; not auto-cleaned)

Feature file vs origin/main: @wip removal only (7 scenarios). No ## Exceptions. Implementation exists (src/isaac/server/burst.clj, wrap-burst before wrap-logging/wrap-auth).

Gates (unset ISAAC_GIT):
- bb features features/server/burst.feature features/server/logging.feature → 10 examples, 0 failures, 30 assertions (35.8s) — not stable
- bb spec spec/isaac/server → 118 examples, 0 failures, 207 assertions
- bb ci → spec 234/0/466 then full features **80 examples, 2 failures, 218 assertions** (69.5s)
- Isolated re-run after `rm -rf target/gherclj/generated/`: `bb features features/server/burst.feature` → **7 examples, 1 failure, 27 assertions** (45.9s)

Reproduced failures:
1. Scenario "thirty unauthenticated requests from one client raise one attention post" (bb ci only): Expected 1 got 0 — `comm/delivery/pending` file count
2. Scenario "a burst that keeps going posts nothing more" (bb ci + isolated re-run): Expected 1 got 0 — same pending-file assertion

Acceptance unmet: attention post to pending is not deterministic. Do not land. Return to worker.


## Verify repair (scrapper@isaac-work-1, 2026-09-11)

branch: bean/isaac-udnm @ 56e39a7 (base origin/main@f61a1f5)

Root cause: the live comm delivery worker raced acceptance assertions and moved burst attention records from `comm/delivery/pending` to `failed` because Discord is intentionally unconfigured in the feature harness. Full-suite root state also leaked into the feature because its Background had no explicit root.

Repair: server `start!` accepts `:start-background-services? false`; the feature harness uses it so pending delivery assertions observe the queue before delivery, and burst.feature owns an isolated `target/burst-state` root. Production defaults remain unchanged. Added app spec for opt-out.

Verified after rebase: `bb ci` green — specs 235/0/467; full features 76/0/215. Burst feature also passed three isolated clean-generation runs (7/0/27 each).



## Landed on main (2026-09-11)

main-sha: isaac-server 7f17654a8ff6f9bf15725c047d3b5a88248c197d
