---
# isaac-cov2
title: 'isaac-cli-server: /cli websocket endpoint that runs CLI commands server-side'
status: draft
type: feature
created_at: 2026-06-26T22:03:27Z
updated_at: 2026-06-26T22:03:27Z
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
