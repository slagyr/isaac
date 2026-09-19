---
# isaac-7fge
title: 'isaac-hooks CI red: http.auth.token is dropped silently in the hooks feature harness (no :http schema composed) — auth is OFF in the run; unknown path 404 instead of 401 (tdlz fallout, 60lm)'
status: completed
type: bug
priority: high
tags:
    - hooks
    - security
    - ci
created_at: 2026-09-19T00:00:41Z
updated_at: 2026-09-19T02:06:54Z
---

## Problem (isaac-hooks CI red since isaac-tdlz landed, 2026-09-18)

`features/hooks.feature:72` "missing bearer token returns 401 even for unknown paths" → got **404**. Diagnosis (planner, control experiment): the Background sets `http.auth.token secret123` (the post-tdlz key). In the hooks feature harness the `:http` config schema — contributed by the isaac-http module's `:isaac.config/schema` — is not composed, so `:http :auth :token` is an unknown nested key and is **dropped silently** (isaac-60lm). `auth/principals` sees no token → `auth-on?` false → every request passes unauthenticated → unknown path 404. Swapping the Background back to the pre-tdlz `server.auth.token` makes the scenario pass, proving the key is the only variable. The other hooks scenarios "pass" with `Bearer secret123` only because auth is off — they prove nothing today.

## Why it matters beyond the red test
The failure mode is "auth silently disabled by a config key the schema does not know". In tests this is a harness gap; in production the same drop would happen if the http module's schema ever failed to compose (module load failure) — the server would come up with NO auth and no error. isaac-60lm (nested unknown keys dropped without warning) is the root; `:http :auth` should never be droppable silently.

## Fix
1. isaac-hooks harness: compose isaac-http's schema in the feature module index (declare the http module in the Grover/fixture setup the way hail's and http's own harnesses do), so `http.auth.token` validates and auth is ON in the run. Add a guard scenario: a request with the WRONG bearer → 401 (proves auth is actually on; the existing valid-token scenarios stay).
2. isaac-http: `auth/principals` (or config load) treats `:http :auth` present-but-unvalidated as a hard error, not a drop — at minimum `:auth/config-dropped :error` and refuse to start with a public bind. Ties to isaac-60lm; if 60lm lands a general "unknown key is an error" rule this leg collapses into it.
3. Sweep the other module harnesses for the same tdlz rename (grep `http.auth.token` / `server.auth.token` under features/): any module whose harness lacks the http schema has auth silently off in its features.

## Acceptance
```
cd isaac-hooks && ISAAC_GIT=1 bb features && bb ci   # hooks.feature:72 green; new wrong-bearer scenario green
cd isaac-server && bb features features/server/auth.feature && bb ci
```
Pins: hooks also carries the dangling foundation pin (isaac-lsz2) — land after or with the lsz2 hooks repin; do not pin foundation main here until the agent leg of lsz2 (config-cache steps) is on agent main.

## Handoff

branch: bean/isaac-7fge

- isaac-hooks @ d56d384c2824f23f4087f698ec219fd4fb5903c0 (base origin/main@0602e9e616625d78fdaa74efd8b08009e2957dc1)
- isaac-http @ 2dab4fbea316a36cae27864b1caa6de1f294dc3a (base origin/main@8edc65cc2b4f001c482390ac52e34094161ab350)

Hooks: pin foundation `df64bf1`, agent `76320fa`, http `8edc65c` (reachable mains; lsz2 agent config-cache is on agent main). Guard scenario "wrong bearer token returns 401 even for unknown paths". `ISAAC_GIT=1 bb features` 20/0.

HTTP: `valid-start?` refuses start on `http.auth*` unknown-key warnings (`:auth/config-dropped`). `start!` forwards `:config-warnings`. Feature harness passes loader warnings into start. `bb spec` 159/0; `bb features features/server/auth.feature` 10/0.

Sweep: remaining `server.auth.token` usages are only in retired-schema scenarios (http/config.feature) or non-main checkouts. Live module features already use `http.auth.token`. isaac-60lm (nested unknown keys as warnings) is still draft; this bean's HTTP leg is the auth-specific hard error.



## Verify fail (attempt 1, 2026-09-19): isaac-http squash conflict vs origin/main (isaac-xc08) on spec/isaac/http/server_steps.clj

HEAD isaac-hooks: d56d384 (bean/isaac-7fge, base origin/main@0602e9e). Working tree: clean.
HEAD isaac-http: 2dab4fb (bean/isaac-7fge, base origin/main@8edc65c). Working tree: clean. origin/main has since moved to 6915e6e (isaac-xc08).

verify.md §7a: a squash conflict is a FAIL, not something to resolve here.

git merge-tree origin/main vs bean/isaac-7fge reports "changed in both" on spec/isaac/http/server_steps.clj:
- bean adds :config-warnings (:warnings load-result) into start-opts (line ~536)
- xc08 (origin/main) rewrote the same start-server helper (fill-burst-defaults, fixture-refuse-401)

Do not land either repo. Rebase isaac-http bean/isaac-7fge onto origin/main@6915e6e, keep both the warnings forward and the xc08 burst/fixture edits, then re-hand.

Hooks branch is FF-able onto origin/main (feature delta is a new scenario + pin bumps only). HTTP is the blocker.

Gates run on the branches (not landed):
- isaac-hooks: bb spec 30/0/44; ISAAC_GIT=1 bb features 20/0/37
- isaac-http: bb spec 159/0/335; ISAAC_GIT=1 bb features 86/0/219


## Verify fail repair (scrapper@isaac-work-1)

Do not land. Do not pin.

isaac-http `bean/isaac-7fge` @ `b907c09` (base origin/main@`6915e6e`). FF-able.
Rebased onto origin/main (isaac-xc08). Kept `:config-warnings` in start-opts
and xc08 `fill-burst-defaults` + `fixture-refuse-401`. merge-tree clean.

isaac-hooks `bean/isaac-7fge` @ `d56d384` left in place (already FF-able).

Gates after rebase:
- isaac-http bb spec 163/0; bb features auth + burst_default 16/0


## Landed on main (2026-09-19)

main-sha: isaac-http 111fff1216b9dbda458ea086e6f3ced5db906ca2
main-sha: isaac-server 111fff1216b9dbda458ea086e6f3ced5db906ca2
main-sha: isaac-hooks 9b51248334b0ef51813c41ea87295b659249df19
