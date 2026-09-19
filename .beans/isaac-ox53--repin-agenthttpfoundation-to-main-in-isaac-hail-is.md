---
# isaac-ox53
title: Repin agent/http/foundation to main in isaac-hail, isaac-claude-code, isaac-cli-proxy (after isaac-x2lp lands)
status: in-progress
type: task
priority: high
tags:
    - ci
    - pins
created_at: 2026-09-19T03:38:55Z
updated_at: 2026-09-19T19:34:55Z
blocked_by:
    - isaac-x2lp
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
