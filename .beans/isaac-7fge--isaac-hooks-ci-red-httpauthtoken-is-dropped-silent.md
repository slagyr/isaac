---
# isaac-7fge
title: 'isaac-hooks CI red: http.auth.token is dropped silently in the hooks feature harness (no :http schema composed) — auth is OFF in the run; unknown path 404 instead of 401 (tdlz fallout, 60lm)'
status: in-progress
type: bug
priority: high
tags:
    - security
    - ci
    - unverified
    - hooks
created_at: 2026-09-19T00:00:41Z
updated_at: 2026-09-19T01:55:50Z
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
