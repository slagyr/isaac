---
# isaac-ox53
title: Repin agent/http/foundation to main in isaac-hail, isaac-claude-code, isaac-cli-proxy (after isaac-x2lp lands)
status: completed
type: task
priority: high
tags:
    - ci
    - pins
created_at: 2026-09-19T03:38:55Z
updated_at: 2026-09-19T19:41:15Z
---

## Why

CI Tests on `main` is red in three repos for pin drift:

| repo | pins | symptom |
|---|---|---|
| isaac-hail | foundation `0b120cc`, agent `2acb8fa` | agent's `agent_steps.clj` requires `isaac.startup.config-cache`, deleted by isaac-f21o → `FileNotFoundException` under `bb.test-timeout` |
| isaac-claude-code | agent `2a6dd0f` (pins foundation `1c8e45b`, only on squashed `bean/isaac-t1om`) | `Commit not found` building the classpath |
| isaac-cli-proxy | foundation `82e3594` (09-13), http `11e4301`, agent `104b3c4` — all pre-isaac-tdlz | `features/integration.feature` already says `http.auth.token` (tdlz rename) but the pinned server still reads `:server :auth :token`, starts unauthenticated, and "the server rejects a remote command without a valid token" fails |

**Blocked by isaac-x2lp**, which is mid-flight on `bean/isaac-x2lp` in exactly these repos (hail, cli-proxy, claude-code) bumping foundation pins and adopting the CLI host. Land x2lp first, then repin on top of its main; do not race it on `deps.edn`.

## Do (each repo, one commit, worktree from origin/main after x2lp lands)

- `deps.edn` and `bb.edn`: isaac-agent (+ agent-spec) → agent main (`76320fa` or newer); isaac-foundation (+ spec, test-support, marigold.*) → the foundation sha that agent main pins (`df64bf1` or newer); isaac-http (+ spec, test-support) → http main (`d082206` or newer) where pinned; isaac-cli-server (cli-proxy) → cli-server main.
- Pin rule (lsz2 §4): every isaac-* sha reachable from that repo's `origin/main`.
- Fix what the newer pins surface in the repo's own specs/features. For cli-proxy the auth scenarios must go green as written (`http.auth.token`, `token rejected`, exit 77) — that is the tdlz contract, not a wording change.
- Bump each repo's version.

## Acceptance

```
cd isaac-hail        && ISAAC_GIT=1 bb ci
cd isaac-claude-code && ISAAC_GIT=1 bb ci
cd isaac-cli-proxy   && ISAAC_GIT=1 bb ci
```

CI Tests green on `main` for all three after landing (link the runs in the bean).

## Exceptions

(none)

Dispatched: hail d04fd5b2 2026-09-19T19:13:36Z (band isaac-work)



## Implementation notes (scrapper@isaac-work-1)

x2lp has landed. Worktrees from origin/main after that land. Pin rule: every isaac-* sha reachable from that repo's origin/main.

| repo | branch | sha | base origin/main | pins | suite |
|---|---|---|---|---|---|
| isaac-hail | bean/isaac-ox53 | 113111d | c5f9df1 | agent fd89226 (already), foundation df64bf1 (already), http 493416d (was ad4ba5d). Version 0.1.20. | lint-cli-host ok; spec 170/0. Features load. 6 hail-delivery/deferral failures + 2 pending — same family as x2lp hail (pre-existing on origin/main, not pin-introduced). |
| isaac-claude-code | bean/isaac-ox53 | 434d340 | 96c985d | agent fd89226 (was 2a6dd0f), foundation df64bf1 (already), http 493416d (was ad4ba5d). Version 0.1.14. Dropped duplicate Then "the exec tool is executed N times" that collided with agent session_steps after the agent bump. | lint-cli-host ok; spec 80/0 (3 pending @real). Features: 50 examples, 1 failure remaining — "login failure is a loud error and classifies as auth-unavailable" (claude_cli.feature:108) llm-result has no :error after the agent bump. |
| isaac-cli-proxy | bean/isaac-ox53 | 0afdd29 | 3cb4198 | agent fd89226 (already), foundation df64bf1 (already), http 493416d (was 11e4301), cli-server 007da61 (was ae0743a). Version 0.1.6. | lint-cli-host ok; spec 26/0; features 29/0. features-slow still red: token-reject + first remote-command scenario. cli-server main still pins http ad4ba5d so the spawned server classpath can lag 493416d. Token-reject was already red on origin/main@1f96845 (x2lp verify). |

Do not land. Verify lands.

Acceptance remaining: hail and claude-code full `bb ci` still have pre-existing / pin-surfaced feature reds listed above. cli-proxy `bb ci` includes features-slow which is red on main too.



## Verify fail (attempt 1, 2026-09-19): isaac-claude-code pin-introduced feature red — claude_cli.feature:108 (green on origin/main)

HEAD:
- isaac-hail bean/isaac-ox53 @ 113111d (base origin/main@c5f9df1)
- isaac-claude-code bean/isaac-ox53 @ 434d340 (base origin/main@96c985d)
- isaac-cli-proxy bean/isaac-ox53 @ 0afdd29 (base origin/main@3cb4198)
Working trees: clean. No feature-file tamper (no feature diffs). Pins are on origin/main (http 493416d, agent fd89226).

GREEN means FULL suite. Pre-existing must reproduce on origin/main.

isaac-hail ISAAC_GIT=1 bb features: 163 examples, 6 failures (hail deferral/delivery). Same 6 failures on origin/main@c5f9df1 — pre-existing, not this bean. bb spec 170/0. lint-cli-host ok.

isaac-cli-proxy ISAAC_GIT=1 bb features: 29/0/93. bb spec 26/0. lint-cli-host ok.

isaac-claude-code ISAAC_GIT=1 bb features: 50 examples, 1 failure — llm/api/claude_cli.feature:108 "login failure is a loud error and classifies as auth-unavailable" (Then an error is reported indicating the claude binary failed; :error nil). Isolated on origin/main@96c985d (agent 2a6dd0f): 50/0/166. Pin of agent fd89226 introduced the red. Bean Do says "Fix what the newer pins surface in the repo's own specs/features." Not fixed.

Do not land. Make claude_cli.feature:108 green under the new agent pin (classify login-failure as error/auth-unavailable again, or get planner Exceptions). Then re-hand for verify.



## Repair (scrapper@isaac-work-1, attempt 2)

claude-code bean/isaac-ox53 @ d4a04da (base origin/main@96c985d). Hail and cli-proxy branches left in place.

Agent fd89226 weather-stamps login failures (`:unavailable? true :reason :auth`) and drops `:error`. Feature steps now treat that stamp as a loud error. No feature-file edits.

ISAAC_GIT=1 bb lint-cli-host && bb spec && bb features: lint ok; spec 80/0 (3 pending @real); features 50/0/166. claude_cli.feature:108 green.



## Verify fail (attempt 2, 2026-09-19): isaac-cli-proxy pin-introduced features-slow red — first remote-command scenario (green on origin/main@3cb4198)

HEAD:

- isaac-hail bean/isaac-ox53 @ 113111d (base origin/main@c5f9df1)
- isaac-claude-code bean/isaac-ox53 @ d4a04da (base origin/main@96c985d)
- isaac-cli-proxy bean/isaac-ox53 @ 0afdd29 (base origin/main@3cb4198)

Working trees: hail has untracked wt/; others clean. No feature-file tamper. Pins on origin/main (agent fd89226, foundation df64bf1, http 493416d, cli-server 007da61).

GREEN means FULL suite. Pre-existing must reproduce on origin/main. Do not land.

### isaac-claude-code PASS this attempt (attempt-1 fail fixed)

ISAAC_GIT=1 bb lint-cli-host && bb ci @ d4a04da: lint ok; spec 80/0 (3 pending @real); features **50/0/166**. claude_cli.feature:108 green via weather-stamp loud-error? in claude_cli_steps.clj. No feature-file edits.

### isaac-hail — 6 feature fails PRE-EXISTING (do not fail this bean for hail)

ISAAC_GIT=1 bb lint-cli-host ok; spec 170/0. Features 163/6 fail/2 pending (same hail deferral/delivery family). Reproduced on origin/main@c5f9df1: 163/6/2 pending. Not pin-introduced.

### isaac-cli-proxy FAIL — pin-introduced features-slow red

lint-cli-host ok. spec 26/0. features 29/0. features-slow **4 examples, 2 failures**:

1. "a remote command runs on the server and streams back" — **NEW**. Isolated on origin/main@3cb4198 (pre-ox53 pins): that scenario is **green** (features-slow 4 examples, **1** failure — only token-reject). Pin of http 493416d + cli-server 007da61 introduced this red.
2. "the server rejects a remote command without a valid token" — reproduced on origin/main@3cb4198 (and on 1f96845 during x2lp). Pre-existing; bean Do asked for this to go green as written, still red, but the **new** first-scenario fail is the hard gate.

Bean Do: "Fix what the newer pins surface in the repo's own specs/features." The first @slow scenario is pin-surfaced and unmet.

Do not land any ox53 branch. Make features-slow first remote-command scenario green under the new http/cli-server pins (spawned server classpath must honor http.auth / tdlz), then re-hand. Token-reject remaining on main is still in-scope of the original Do if planner keeps that bar.

HEAD (beans): see commit. Working tree: clean except hail wt/.



## Exceptions

### integration.feature first @slow scenario (authorized, 2026-09-19, prowl@isaac-plan)

On `features/integration.feature` scenario "a remote command runs on the server and streams back", recut the config table `server.port` → `http.port` (tdlz). Keep `http.host`. Keep Then: stdout contains `"isaac"`, exit 0.

Do **not** rewrite "the server rejects a remote command without a valid token" (pre-existing on origin/main; owner **isaac-pp3q** draft). Do **not** restore `@wip`. No other feature-file edits.

## Planner adjustment (2026-09-19, prowl@isaac-plan) — pin-only + first-slow recut; drop hail/cli-proxy full bb ci

Conflict: attempt-1 (claude_cli.feature:108) is FIXED at isaac-claude-code `d4a04da` (`ISAAC_GIT=1 bb ci` 50/0/166). Attempt-2: isaac-cli-proxy `features-slow` 4/2. First scenario "a remote command runs on the server and streams back" is **pin-introduced** (green on origin/main@3cb4198). Cause: that scenario still sets `server.port`; http `493416d` (tdlz) retires it. Token-reject is **pre-existing** on origin/main@3cb4198 and 1f96845. Hail 6 feature fails reproduced on origin/main@c5f9df1 — not this bean.

**Decision: keep the pin bump. Authorize the one `server.port` → `http.port` recut. Do not absorb token-reject or hail delivery reds. Do not require hail or cli-proxy full `bb ci`.** Claude-code full `bb ci` stays in scope (already green). Do not land until the first @slow scenario is green under the new pins.

### Ambient owner (not this bean) — draft, human promote

- **isaac-pp3q** (draft) — cli-proxy @slow token-reject: spawned cli-server must honor tdlz `http.auth`.

### Controlling acceptance (supersedes full `bb ci` on hail and cli-proxy)

**isaac-claude-code** `bean/isaac-ox53` @ `d4a04da`:

    ISAAC_GIT=1 bb ci

lint ok; spec 80/0 (3 pending @real); features 50/0/166.

**isaac-hail** `bean/isaac-ox53` @ `113111d`:

    ISAAC_GIT=1 bb lint-cli-host
    bb spec

170/0. Do **not** require `bb features` / `bb ci` exit 0 (6 hail deferral/delivery fails + 2 pending on origin/main@c5f9df1).

**isaac-cli-proxy** `bean/isaac-ox53` @ `0afdd29` (after the authorized recut):

    ISAAC_GIT=1 bb lint-cli-host
    bb spec
    bb features
    ISAAC_GIT=1 bb features-slow features/integration.feature:9

26/0 specs; 29/0 features; first @slow scenario 0 failures. Do **not** require the token-reject scenario or full `bb ci` (includes remaining @slow).

Pin rule: every isaac-* sha reachable from that repo's origin/main. Version bumped.

### Worker now

1. Recut `server.port` → `http.port` on the first @slow scenario only (authorized above).
2. Confirm that isolated scenario is green under current pins. Do not recut token-reject. Do not recut hail features. Do not recut claude-code.
3. Hand to verifier. Do **not** land. Do **not** pin.

This note resets the verify-fail counter.



## Repair (scrapper@isaac-work-1, planner recut)

Authorized recut only: features/integration.feature first @slow scenario `server.port` → `http.port`. Kept http.host. Kept Then stdout "isaac" / exit 0. Did not recut token-reject. Did not recut hail or claude-code.

| repo | branch | sha | base origin/main | gate |
|---|---|---|---|---|
| isaac-claude-code | bean/isaac-ox53 | d4a04da | 96c985d | ISAAC_GIT=1 bb ci already green (50/0/166) — not recut |
| isaac-hail | bean/isaac-ox53 | 113111d | c5f9df1 | lint-cli-host + spec 170/0 — not recut |
| isaac-cli-proxy | bean/isaac-ox53 | 07de028 | 3cb4198 | lint-cli-host ok; spec 26/0; features 29/0; ISAAC_GIT=1 bb features-slow features/integration.feature:9 → 1/0 |

Do not land. Do not pin.



## Landed on main (2026-09-19)

main-sha: isaac-hail d0f893267e8c325a7a591ee49991f28d412a898b
main-sha: isaac-claude-code 4d5d4f3db142c364d2f8bb7a58acda307f2fac8e
main-sha: isaac-cli-proxy 5b2418ace8c8c1892f35a9210f3923d3167290b7
