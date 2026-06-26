---
# isaac-7p1i
title: 'isaac-cli-proxy: remote CLI client that ships argv+IO over /cli'
status: draft
type: feature
created_at: 2026-06-26T22:03:27Z
updated_at: 2026-06-26T22:03:27Z
parent: isaac-ec9q
---

Build the CLIENT half of the remote-CLI epic (isaac-ec9q) in repo https://github.com/slagyr/isaac-cli-proxy. A thin proxy: `isaac remote <url>/cli <command...>` ships argv + pipes local stdio to the server's `/cli` endpoint and exits with its code.

## Scope
- `isaac remote <url>/cli <command...>`: open a full-duplex websocket to the server, send argv (+ cwd/root) + auth token, pipe local stdin up and stdout/stderr down, exit with the server's exit code.
- No command (just the URL) -> print the server's usage.
- Full-duplex so `isaac remote .../cli acp` drives a remote agent (editor speaks ACP over local stdio, bytes relayed) — subsumes the acp `--remote` proxy.
- Resilience carried over from the acp proxy: reconnect/never-give-up (isaac-9rdk), stdin serialization/ordering (isaac-ob1n), message framing.
- Auth: pass a bearer token (flag/env), like acp `--remote -t`.

## Tests (client side, command-agnostic)
- argv + IO + exit-code round-trip against a stub server; streaming + reconnect; usage passthrough.

## Coordination
Shares the wire/framing protocol with isaac-cli-server. Once this lands, DEPRECATE acp `--remote`/`-r`/`-t` in favor of `isaac remote .../cli acp`.
