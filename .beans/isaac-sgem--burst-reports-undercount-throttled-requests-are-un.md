---
# isaac-sgem
title: 'Burst reports undercount: throttled requests are uncounted, unlogged, and do not refresh the cooldown'
status: in-progress
type: bug
priority: normal
tags:
    - security
    - http
created_at: 2026-09-20T18:53:00Z
updated_at: 2026-09-20T20:48:40Z
---

Repo: **isaac-http**. Micah, 2026-09-20, reading a real burst report from zanebot:

```
Unauthenticated burst from 8.234.49.147: 10 requests in 60000ms, paths like /, /.git/config, /.env, ...
Unauthenticated burst from 8.234.49.147 ended: 10 requests in 623834ms
```

He asked why 623,834ms, and doubted that only 10 requests were involved. The log says the scan itself was **1.4 seconds** — ten 401s between 13:32:18.5 and 13:32:19.9 — then the scanner left. So the count was honest this time, but only by luck: it stopped the moment it was flagged.

## What the two numbers actually mean

- **`in 60000ms` on the detect post** is the configured window, not an observed duration. The ten requests took 1.4s.
- **`in 623834ms` on the end post** is `now - first-ms` where `now` is whenever `sweep-ended!` happens to run. The sweep cannot fire until the client has been quiet for `cooldown-ms` (600000), and it only runs on the next inbound request. So the number is ~1.4s of scanning + 10min cooldown + 22s of waiting for a request to trigger the sweep. It will always be ≳ cooldown, which makes it useless for judging an attack.

## The real defect: throttled requests are invisible

`wrap-burst` short-circuits a flagged client with a bare 429 **before** the handler, and `record-unauthenticated!` only counts 401/403 **from** the handler. Two consequences, neither visible in this incident because the scanner gave up:

1. **The total never grows past the detection count.** Every request we actually blocked is uncounted. A scanner that kept hammering would still be reported as "10 requests".
2. **`:last-ms` never refreshes while throttling**, so the cooldown clock runs from the last request that reached auth. A client throttled continuously for 20 minutes is declared "ended" ten minutes in, mid-attack.

`wrap-logging` also sits **inside** `wrap-burst`, so throttled requests produce no `http/request` line either — the block is unauditable.

## Change

- Count throttled hits in their own counter, refreshing `:last-ms`. Detect stays on refused (401/403) hits, so the threshold keeps its meaning.
- `:server/burst-ended` carries `:total` (refused), `:throttled`, and `:duration-ms` measured **first hit → last hit**, not first hit → sweep.
- The attention post reads e.g. `… ended: 30 refused, 20 throttled over 30000ms`.
- Log throttled requests (`http/request` at info, or a debug line naming client, uri and 429) so a block leaves a trace.
- The detect post keeps naming the window explicitly, so `in 60000ms` is not mistaken for an observed span — reword to `10 refused within a 60000ms window` if that reads better.

## Scenarios (committed `@wip` on isaac-http main 5f7f24e, `features/server/burst.feature`)

| scenario | asserts |
|---|---|
| the ended post counts throttled requests, not just the ones that reached auth | `burst-ended` has `total 30` and `throttled 20`; the post contains "30 refused" and "20 throttled" |
| throttled traffic keeps the burst alive | throttled hits at 10:09 push the cooldown out; no `burst-ended` at 10:10:01, one at 10:19:01 |
| the ended post reports the burst's own span, not the wait for the sweep | `duration-ms 30000` and the post contains "30000ms", not the ~631000ms to the sweep |

## Step ledger

| step | status |
|---|---|
| an Isaac root at … / config: / the Isaac server is started / the clock is fixed at … / the client sends GET … N times / the log has entries matching: / the log has no entries matching: / the only file in … EDN contains: | reuse |

No new steps.

## Acceptance

`@wip` removed and

```
cd isaac-http && bb features features/server/burst.feature && bb features features/server/burst_default.feature && bb ci
```

Keep the existing seven scenarios green — particularly "a burst that keeps going posts nothing more", which must still hold: more counting, not more posts.

feature-baseline: isaac-http 5f7f24e8708dab9f2370138f9702a68d591ce16b
feature-blob: isaac-http features/server/burst.feature 4c7c82045989a66cdb1cdc8e3706b359ad4cafc2

Dispatched: hail 0c35e0b0 2026-09-20T18:58Z (band isaac-work)

Dispatched: hail b377e10d 2026-09-20T20:17:51Z (band isaac-work)

## Conflict: two baselined scenarios use "the only file in" where two posts exist (2026-09-20)

Implementation is done and green; the **scenarios contradict a scenario that is
already on main**. Handed back to the planner — the fix is one word in each of
two baselined scenarios, which a worker may not make.

### What was built (branch `bean/isaac-sgem`, isaac-http, base `5f7f24e`)

- `burst/record-throttled!` — counts a 429'd request in its own `:throttled`
  counter and refreshes `:last-ms`; never feeds detection, so the threshold
  keeps its meaning. Called from `wrap-burst` before `throttled-response!`.
- `:server/burst-ended` now carries `:total` (refused), `:throttled` and
  `:duration-ms` measured **first hit → last hit** (`burst-span-ms`), not
  first hit → sweep.
- Ended attention post: `Unauthenticated burst from <client> ended: 30 refused,
  20 throttled over 30000ms`.
- Every throttled request logs `:server/burst-throttled-request` at debug with
  `:client`, `:uri`, `:status 429` (the once-per-burst `:server/burst-throttled`
  info line is unchanged, so the existing throttle scenario still holds).
- Detect post wording left alone on purpose: scenario "thirty unauthenticated
  requests from one client raise one attention post" asserts `"30 requests"`,
  and it is baselined.

`bb spec` 190/0. `bb features` 107 examples, **2 failures** — both described
below. `bb bean-gate verify isaac-sgem` → `PASS (isaac-http @ HEAD 8d88c26)`,
exit 0: the only feature edit was removing the three `@wip` tags.

### The contradiction

```
1) Unauthenticated burst control the ended post counts throttled requests, not just the ones that reached auth
   Expected: 1
        got: 2 (using =)
2) Unauthenticated burst control the ended post reports the burst's own span, not the wait for the sweep
   Expected: 1
        got: 2 (using =)
```

Both new scenarios end with

    And the only file in "comm/delivery/pending" EDN contains:

`isaac.foundation.fs-steps/only-file-in-edn-contains` asserts
`(should= 1 (count children))` before reading. But at that point the directory
holds **two** posts — the detect post from crossing the threshold and the ended
post from the sweep — and that is required behaviour: the already-green
scenario "a quiet cooldown ends the burst with a total" asserts

    And the directory "comm/delivery/pending" has exactly 2 files

for exactly this sequence. No implementation can make both true. The 2 is the
step's file count, not a count of posts per burst — "a burst that keeps going
posts nothing more" is still green (1 detect post for 90 requests).

### The one-word fix (verified, then reverted)

isaac-http already defines `the newest file in "<dir>" EDN contains:`
(`spec/isaac/http/server_steps.clj:1308`, `newest-file-in-edn-contains`, sorts
by `:created-at`). Swapping `the only file in` → `the newest file in` in those
two scenarios only — `features/server/burst.feature:102` and `:133` — makes the
file green:

    bb features features/server/burst.feature → 10 examples, 0 failures, 37 assertions

That edit was made to prove the implementation, then reverted; the branch holds
the baselined text with `@wip` removed and nothing else.

Asked of the planner: make that step swap on isaac-http `main`, re-baseline, and
hand the bean back. Nothing else about the bean changes.

### Environment note (not caused by this bean)

`bb ci` aborts before the suites at the `pins` task: it shells
`../isaac-foundation/libexec/isaac modules pins`, and that shared sibling is
parked on `bean/isaac-3kol` (0.1.25, `b10519c`), a build with no `modules`
command → `Unknown command: modules`, `Error while executing task: pins`. The
sibling is load-bearing for another session, so it was left alone and `bb spec`
/ `bb features` were run directly.
