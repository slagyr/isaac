---
# isaac-w0yx
title: 'cli-server + cli-proxy: server-only commands find their server via state/cli-server.edn and a local token'
status: in-progress
type: feature
priority: high
tags:
    - cli
created_at: 2026-11-16T18:28:12Z
updated_at: 2026-11-16T19:23:46Z
blocked_by:
    - isaac-dcxk
---

Repos: **isaac-cli-server** (server file + token) and **isaac-cli-proxy** (the forwarder). Bean B of three; needs isaac-dcxk (the :server-only flag and :isaac/cli-forwarder berth). Spec: `isaac-cli-proxy/features/server_only.feature` (@slow integration, real server).

## What

- **isaac-cli-server**: when /cli starts, write `<root>/state/cli-server.edn` = `{:url "ws://127.0.0.1:<bound port>/cli" :token "<random, ≥20 chars>"}`, file mode 600 (written so it is never readable by group/world, even briefly). Accept that token on /cli alongside the configured auth. A restart writes a new token. Remove the file on clean shutdown.
- **isaac-cli-proxy**: contribute the `:isaac/cli-forwarder` from isaac-dcxk. For a :server-only command:
  1. a global `--remote <url>` option wins (credentials resolve as for `isaac remote`);
  2. else read `state/cli-server.edn` in the root and connect with its token;
  3. no file, or nothing answers at the url (connection refused) → `{:no-server true}` (runs locally);
  4. a server that answers and refuses the token → stderr "token rejected", exit 77, **no local fallback**.
  Otherwise stream the command exactly like `isaac remote … -- <argv>` and return its exit code.
- **Loopback is not trusted** (Micah, 2026-11-16): Tailscale Funnel proxies public traffic to 127.0.0.1, so every local connection authenticates with a token.
- **New steps / fixtures** (cli-proxy features): `the fixture command {name} is registered as server-only` (fx-where prints `embedded` when dispatched embedded, `process` otherwise), `the isaac file {path} has mode {mode}`, `the token in {path} is replaced with {token}`.

## Acceptance

`bb features-slow features/server_only.feature` green in isaac-cli-proxy with `@wip` removed (6 scenarios), plus each repo's `bb spec`, `bb features` and `bb features-slow` green. Pin both repos to isaac-dcxk's foundation sha. Do not bump versions (the deploy train does).

feature-baseline: isaac-cli-proxy 5f94704ceebe9f9701677f2c48cca58b310af0bb
feature-blob: isaac-cli-proxy features/server_only.feature 475223627959c323ac343a856cb3be692298c7b9

## Planner note (2026-11-16): foundation pin

isaac-dcxk landed: pin isaac-foundation (deps.edn and bb.edn, including the spec/test-support roots) to `ab9f1c5711c7f85aefc67100bdaef1b80fe7a736`.


## Checkpoint (2026-11-16)

Done: claimed; isolated worktrees `../isaac-cli-server-w0yx` and `../isaac-cli-proxy-w0yx`, branches pushed at a6b2729 and 0191b48. Foundation pins changed to ab9f1c5 in both repos. Added local credential component + verifier (server), forwarder + initial specs (proxy); focused `bb spec spec/isaac/cli_server/local_spec.clj` and `bb spec spec/isaac/cli_proxy/forwarder_spec.clj` green. These are intermediate only, not acceptance.

Done: all 6 server_only scenarios pass; both repos bb ci green (proxy includes all slow scenarios); bb bean-gate verify isaac-w0yx --dir isaac-cli-server=../isaac-cli-server-w0yx --dir isaac-cli-proxy=../isaac-cli-proxy-w0yx PASS. Both branches rebase/CI green. Server squash commit b0356e1 created on local main, NOT pushed yet. Proxy branch 36c590e pushed; sibling proxy main has unrelated dirty changes, do not touch. Next: squash proxy branch in isolated worktree against origin/main, re-run gate on squash refs, push both mains, record main-sha and complete. Resume at ../isaac-cli-proxy-w0yx/src/isaac/cli_proxy/explicit.clj:10; command git worktree add ../isaac-cli-proxy-w0yx-land --detach origin/main from proxy branch checkout. No tests red.


## Landed on main (2026-11-16)

main-sha: isaac-cli-server b0356e1406992f1b9813f384f539797c0689ddb9
main-sha: isaac-cli-proxy e71020addd5a08aff213c4564ee2b67902428766

Verification: bb ci both repos; bb features-slow features/server_only.feature (6 examples, 0 failures); bean-gate PASS against squash commits.
