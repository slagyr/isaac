---
# isaac-xc08
title: HTTP burst control on by default (zanebot values), off with :http :burst :enabled false; knobs override individually; hot-reload
status: in-progress
type: feature
priority: high
tags:
    - http
    - security
    - unverified
created_at: 2026-09-19T01:25:54Z
updated_at: 2026-09-19T01:51:32Z
---

Micah (2026-09-18): "HTTP throttling burst control should be on by default, with a way to turn it off." The earlier conversation left no bean; this is it.

## Today
isaac-udnm shipped burst control opt-in: `:http :burst` absent ⇒ `wrap-burst` is a pass-through (http.clj `wrap-burst`, manifest "Absent group = off"). zanebot has it configured explicitly (`{:threshold 30 :window-ms 60000 :cooldown-ms 600000 :throttle? true}`); yopp and any fresh install have none. A public server should never run without it.

## Decision
- **Default ON**: `{:enabled true :threshold 10 :window-ms 60000 :cooldown-ms 600000 :throttle? true :notify? true}`. (Micah 2026-09-18: 30 is too loose; only REFUSED requests — 401/403 — count, so a legitimate client never gets near 10 in a minute while a scanner is cut off after its first ten probes.) zanebot's explicit 30 is overridden by deleting its block or setting 10.
- **Off = `:http :burst {:enabled false}`** (new boolean knob; schema default true). Explicit knobs override one at a time; the rest keep defaults. Hot-reloadable like the rest of `:http` (isaac-s9e3 seam).
- Defaults live in the HTTP module's schema (`:default` on each knob) so `config get http.burst` shows the effective values and `config validate` accepts an absent group. `wrap-burst` reads the resolved config; the only "off" is `:enabled false`.
- Loopback is still never throttled (udnm rule unchanged).
- **Count by response status, not by wrap-auth's branch (Micah, 2026-09-18).** `wrap-burst` observes the response: any 401/403 from ANY source (wrap-auth, or a route that verifies its own credential — the Google push door's OIDC check, isaac-1jep) counts toward the client's burst. `record-unauthenticated!` moves out of `wrap-auth`; nothing a route does needs to know burst control exists. Authenticated 2xx traffic never counts — a leaked-token flood is a principals/audit problem (isaac-gym1), not a burst one.

## Scenarios (committed @wip — isaac-http `features/server/burst_default.feature` @ 8edc65c)

| line | scenario |
|------|----------|
| :21 | with no burst config at all, ten unauthenticated requests trip a burst and the client is throttled |
| :32 | burst control can be turned off explicitly (`http.burst.enabled false`) |
| :45 | an explicit knob overrides its default and the others keep theirs |
| :58 | turning burst control off on hot reload releases a throttled client |
| :72 | the effective burst config is visible with its defaults filled in (`config get http.burst`) |
| :88 | a route that refuses on its own counts toward the burst (response-status counting) |

## Step ledger

| step | status |
|------|--------|
| an Isaac root at … / config: / the Isaac server is started / config is updated: / the client sends GET|POST … with header … N times / the response status is … / the log has (no) entries matching: / the directory … has exactly N file(s) / isaac is run with … / the stdout EDN contains: | reuse (burst.feature / auth.feature / foundation cli_steps; `the client sends POST … N times` — confirm the existing sender accepts POST, else add the verb) |
| principal … is configured with secret … and scopes … | reuse (isaac-bzgw) |
| **a fixture route {method} {path} declares no scope and refuses every request with 401** | **NEW — sibling of bzgw's `a fixture route … declares no scope`; the handler itself returns 401** |

One new step (a fixture-route variant). Note `the directory … has exactly 0 files` / `1 file` — burst.feature uses both singular and plural; match whichever the step accepts.

## Acceptance
```
cd isaac-server && bb features features/server/burst_default.feature features/server/burst.feature && bb ci
```
burst.feature's "config schema lists the burst knobs" gains `enabled`. Version bump; rides the http train. Field: zanebot's explicit `:burst` block can then be deleted (one-time) and `config get http.burst` still shows it on; yopp gets burst control on its next http upgrade with no config change.


## Handoff (scrapper@isaac-work-1)

branch: bean/isaac-xc08 @ efc713c (base origin/main@8edc65c). FF-able.

Burst control is ON by default (`burst/defaults` + schema `:default` / `[:default …]` coercions).
Off only with `:http :burst :enabled false`. wrap-burst counts 401/403 on the
response after the handler (self-authenticating routes included). wrap-auth no
longer records hits. Explicit knobs override one at a time; hot-reload
`enabled false` releases a throttled client.

Acceptance:
- bb features features/server/burst_default.feature features/server/burst.feature — 13/0/42
- bb spec — 161/0
- @wip removed
- version 0.1.17

Do not land. Do not pin.
