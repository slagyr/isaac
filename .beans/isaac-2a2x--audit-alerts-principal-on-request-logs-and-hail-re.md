---
# isaac-2a2x
title: 'Audit + alerts: :principal on request logs and hail records; last-used; Discord alerts for 401 bursts, first use, expired/revoked use, expiring soon'
status: in-progress
type: feature
priority: normal
tags:
    - security
    - unverified
created_at: 2026-09-18T04:13:56Z
updated_at: 2026-09-18T20:50:24Z
parent: isaac-gym1
blocked_by:
    - isaac-bzgw
---

Child 4 of isaac-gym1 (principals epic). Blocked by child 1. Repos: isaac-http (audit, alerts), isaac-hail (principal on records).

## Work
- Every request log line (`:http/request` or equivalent) carries `:principal name`; 401/403 lines carry `:principal nil|name` and `:reason :unknown|:expired|:scope`.
- Hail records and deliveries carry `:principal` (from `:isaac/principal` on the request); `isaac hail show` prints it.
- Last-used: `~/.isaac/state/auth/last-used.edn` (or the existing state dir convention), updated at most once per principal per minute (not per request) — for `auth list`.
- Alerts through the existing notification comm (Discord `isaac` channel, same path the crews use): (a) ≥N 401s from one address in a minute (reuse burst thresholds), (b) first ever use of a principal, (c) any use of an expired or revoked token, (d) a principal expiring within 7 days (daily). Hot-reloadable thresholds under `:server :auth :alerts`.

## Scenarios (draft)
1. an authenticated request logs its principal; a refused one logs the reason
2. a hail sent by principal ci is recorded with :principal ci
3. first use of a principal raises one notification; the second use does not
4. use of an expired token raises a notification
5. last-used is written for a principal and shown by `auth list`



## Scenarios (committed @wip)

isaac-http `features/server/auth_audit.feature` @ cacb263:

| line | scenario |
|------|----------|
| :24 | an authenticated request is attributed to its principal in the log |
| :33 | a refused request logs the reason and the principal when known |
| :47 | the first ever use of a principal raises one attention post, later uses none |
| :61 | last-used is recorded for the principal and shown by auth list |
| :74 | last-used is written at most once a minute per principal |
| :84 | use of an expired secret raises an attention post naming the principal |
| :94 | use of a revoked secret raises an attention post naming the principal (`:reason :revoked` — a hash that was valid earlier this process; requires remembering revoked hashes in memory) |
| :110 | a principal expiring within seven days is flagged once a day |
| :126 | alerts can be turned off per kind without a restart (`:server :auth :alerts {:first-use false …}`) |

isaac-hail `features/http.feature` @ a834cf1: a hail record and its delivery carry the sending principal; `hail show` prints it.

Attention posts go through the same `attention.notify` path burst detection uses (`comm/delivery/pending`). Last-used lives at `state/auth/last-used.edn` `{name iso-ts}`; "first use" = no entry for the name.

## Step ledger

| step | status |
|------|--------|
| an Isaac root at … / config: / the Isaac server is started / the isaac config is reloaded / the clock is fixed at … | reuse |
| the client sends GET … with header … (+ N times) / the response status is … / the log has entries matching: | reuse |
| the directory … has exactly N file / the only file in … EDN contains: | reuse (burst.feature) |
| the isaac file … exists with: / isaac is run with … / the stdout lines match: | reuse |
| principal … is configured … / … is removed from config / a fixture route … | reuse (isaac-bzgw) |
| hail: a POST request is made to …: / the sole pending|delivery hail EDN contains: / the hail router ticks / the following sessions exist: | reuse |
| **the clock advances {n} seconds** / **the clock advances {n} days** | **NEW — foundation spec-support next to `the clock is fixed at` (generic)** |
| **the file {path} was written exactly {n} times** | **NEW — foundation spec-support; counts writes through the runtime fs (generic)** |
| **the newest file in {dir} EDN contains:** | **NEW — sibling of `the only file in … EDN contains:` (isaac-http spec-support)** |
| **the auth expiry sweep runs** | **NEW (isaac-http) — invokes the daily sweep directly instead of waiting on the scheduler** |
| **`<the sole hail id>` placeholder** in `isaac is run with "hail show <the sole hail id>"` | **NEW (hail) — substitutes the id of the sole pending hail** |

Five new step families (two generic → foundation spec-support).

## Acceptance
```
cd isaac-server && bb features features/server/auth_audit.feature && bb ci
cd isaac-hail && bb features features/http.feature && bb ci
```
Version bumps; pins are a train step (rides with isaac-4o6r's train or after).

Dispatched: hail 6e179553 2026-09-18T06:14Z (band isaac-work)

## Resume (isaac-work-1, 2026-09-18)

Checkpoint after wrap-up. Implementation on `bean/isaac-2a2x` (not landed).

- isaac-hail `878976c` (base origin/main@8dbba29): POST /hail/send stamps `:principal`; `isaac hail show`; `@wip` removed; version 0.1.18. Unit specs green (33/0). `bb features features/http.feature` still pending the 2a2x scenario — hail `bb.edn` pins isaac-http to origin SHA `11e4301`, so native features load the published server-steps, not this worktree. Resume: point hail features at the local isaac-http-2a2x tree (`../isaac-server-2a2x` or equivalent) then re-run `bb features features/http.feature`.
- isaac-http `b3f8cc9` (base origin/main@ad4ba5d): audit.clj, wrap-auth revoked/remember/record-use, auth-list last-used, clock/sweep/newest-file steps, `@wip` stripped, version 0.1.16. Unit specs green (60/0). `bb features features/server/auth_audit.feature` timed out (180s) this turn — resume from that command.

Next: green the two acceptance feature runs, then `bb ci` per repo, rebase FF onto origin/main, `beans update isaac-2a2x --tag=unverified`, hail isaac-verify reply_to 6e179553.

Dispatched: hail c3052eaf 2026-09-18T18:23Z (session isaac-work-1, continuation of 6e179553 — turn ended at cycle budget without hand-off)

## Resume (isaac-work-1, 2026-09-18 continuation c3052eaf)

Rebased both branches onto current origin/main (conflicts resolved).

- isaac-hail `bean/isaac-2a2x` @ `f95b2e0` (base origin/main@4fa70e2). Merged 4o6r prompt-override + principal stamp. Not pushed.
- isaac-http `bean/isaac-2a2x` @ `3789b2f` (base origin/main@db2b639). wrap-auth uses `:http :burst`; audit reads `:http :auth :alerts` / `:http :auth :principals`. Unit specs 133/0. Not fully pushed.

Still red / unfinished:
- `ISAAC_GIT=1 bb features features/server/auth_audit.feature` still times out at 180s after 4 green dots (scenario 5 `isaac is run with "server auth list"` likely hangs because `isaac.http.cli/run` `block!`s when `auth list` is not dispatched). Resume: isolate that scenario; `run-fn` already special-cases `auth list` — check whether `main/run` reaches it or falls through to `run`/`block!`.
- Hail `bb features features/http.feature` not re-run. Native hail still pins isaac-http `ad4ba5d`. Point hail classpath at `../isaac-server-2a2x` (do not rename shared `../isaac-http`).

Do not hand off until both acceptance feature runs are green.

Dispatched: hail 3e9b6500 2026-09-18T19:58:03Z (session isaac-work-1, continuation of c3052eaf — turn ended at cycle budget without hand-off)


## Handoff (isaac-work-1, 2026-09-18 continuation 3e9b6500)

Ready for verify. Status stays in-progress + tag=unverified. Do not land — verify lands.

- isaac-http `bean/isaac-2a2x` @ `5fd166a` (base origin/main@`db2b639`). Pushed.
  Audit last-used, wrap-auth :revoked/remember/record-use, auth-list intercept, namespaced scopes, headerless regex stdout-lines-match. First-use attention skipped for the synthesized `:admin` legacy token (burst quiet-cooldown extra pending file). Version 0.1.16.
  `ISAAC_GIT=1 bb features features/server/auth_audit.feature` 9/0/36.
  `ISAAC_GIT=1 bb ci` 137 spec + 75 features, 0 failures.
- isaac-hail `bean/isaac-2a2x` @ `f95b2e0` (base origin/main@`4fa70e2`). Pushed.
  POST /hail/send stamps `:principal`; `isaac hail show` prints EDN. Version 0.1.18.
  `bb ci` 166 spec + 156 features, 0 failures, 2 pending (pre-existing hail-get search).

Pins are a train step (rides with isaac-4o6r).
