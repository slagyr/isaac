---
# isaac-cov2
title: 'isaac-cli-server: /cli websocket endpoint that runs CLI commands server-side'
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-26T22:03:27Z
updated_at: 2026-06-27T03:59:00Z
parent: isaac-ec9q
---

Build the SERVER half of the remote-CLI epic (isaac-ec9q) in repo https://github.com/slagyr/isaac-cli-server. Exposes a `/cli` route (mounted by isaac-server like `/acp`) that runs main CLI commands server-side and pipes IO back over a full-duplex websocket.

## Scope
- Mount `/cli` ws endpoint on isaac-server (authenticated; reuse the `/acp` bearer-token auth).
- On connect: receive argv (+ cwd/root) and auth token.
- Run the main CLI dispatch with that argv; pipe stdin (client->server), stdout/stderr (server->client, kept separate), and the final exit code back, via the shared framing protocol.
- Full-duplex streaming so long-lived interactive commands (acp, chat) work from DAY 1 — not just batch.
- Execution model: lean subprocess per invocation (clean isolation for acp streaming + per-session state) vs in-process (bind *in*/*out*/*err*) — decide in design.
- cwd/filesystem: commands run with the SERVER's cwd; honor a client-passed --cwd/root.

## Auth
NON-NEGOTIABLE — `/cli` is remote-shell/RCE-equivalent (server privileges, store, fs). Bearer token like `/acp`; reject unauthenticated. Consider command scoping.

## Tests (server side, command-agnostic)
- argv + stdin/stdout/stderr + exit-code round-trip; streaming/backpressure/ordering; auth enforced; unauthenticated rejected.
- Remote SELECTION needs zero dedicated tests — it's prompt/sessions run server-side, already covered.

## Coordination
Shares the wire/framing protocol with isaac-cli-proxy (client) — define once, keep in lockstep. Subsumes the bespoke acp `/acp` proxy server.

## Feature design (2026-06-26, reviewed with Micah)

Home: `isaac-cli-server/features/cli/endpoint.feature`.

### Test level — handler-level (NOT a booted server)
Drive `isaac.cli-server.ws/handler` directly with a fake ws connection (capturing channel); no real server, no port. Fast/isolated. The real-server boot is reserved for ONE end-to-end integration feature under isaac-ec9q (real server + real `isaac remote` proxy, @slow).

### Handler responsibilities
- Performs the **ws upgrade** itself (like acp's handler: require `:websocket?`, upgrade, then frame loop).
- Does **NO auth** — the Isaac server authenticates all inbound HTTP at the layer BEFORE the handler (the `server.auth.token` check that already guards `/acp`, isaac-g69y). `/cli` inherits it. 401s are server behavior, asserted once at the ec9q integration level, not here.
- After upgrade: read `{:type "start" :argv [...] :cwd ...}`, run the main CLI dispatch, frame stdout/stderr + exit.

### Assertion convention (TABLES.md matcher DSL)
One reusable step `the handler sends frames:` + a subsequence-ordered table; cells are matchers (`#"regex"`, `#*` any-non-nil, exact, blank = no constraint). Covers stdout/stderr/exit/error rows.

### Scenario 1 (LOCKED) — M1 batch round-trip
```
Scenario: a batch command streams stdout and exits zero
  Given the cli-server handler
  When a /cli client sends start with argv ["--version"]
  Then the handler sends frames:
    | type   | data     | code |
    | stdout | #"isaac" |      |
    | exit   |          | 0    |
```

### Remaining scenarios to drive (same style)
- M1: empty argv -> usage frame + exit 0
- M2: stderr framed separately; nonzero exit relayed
- M2: stdin frames piped to the command's stdin (+ stdin-close = EOF)
- M3 (interactive/full-duplex hold-open + reconnect) tracked separately

New-territory: revisit the shape as implementation reveals constraints.


## Implementation (work-3)
- Repo: isaac-cli-server — `isaac.cli-server.ws/handler` + `dispatch` (in-process `main/run` for M1).
- Features: `features/cli/endpoint.feature` — locked batch scenario + empty argv, CLI errors, stdin acceptance.
- Steps: `the handler sends frames:` with TABLES.md matchers (base64 `data` decoded for assertions).
- Auth intentionally omitted in handler (server layer); M3 interactive/reconnect deferred.
- Verified: `bb ci` — 1 spec + 5 features green (dev-local).
