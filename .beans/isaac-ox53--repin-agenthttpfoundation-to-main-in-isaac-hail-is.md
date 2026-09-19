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
updated_at: 2026-09-19T19:13:49Z
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
