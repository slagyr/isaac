---
# isaac-7p1i
title: 'isaac-cli-proxy: remote CLI client that ships argv+IO over /cli'
status: todo
type: feature
priority: normal
created_at: 2026-06-26T22:03:27Z
updated_at: 2026-06-27T04:00:00Z
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

## Feature design (2026-06-26, reviewed with Micah)

Home: `isaac-cli-proxy/features/remote.feature`. Tested against a STUB /cli server (no real server/booted endpoint). Real end-to-end lives in the isaac-ec9q integration feature.

### Command shape & proxy responsibilities
- `isaac remote <url> -- <cmd...>` — `--` delimits the remote argv from the proxy's own options (so `--version`/flags ship cleanly). tools.cli puts everything after `--` in :arguments.
- Proxy is dumb: open ws to <url>/cli, send `{:type start :argv [...post-`--`...]}`, pipe local stdin up as `stdin` frames (+ `stdin-close` on EOF), render `stdout`/`stderr` frames to the matching LOCAL stream, exit the process with the server's `{:type exit :code}`.
- **Usage is delegated to the server** (no command -> send empty argv `[]`; render whatever the server returns) — only the server knows its loaded command set.
- **Auth role = pass-through only**: `--token` presented as the bearer credential on the connection; the SERVER authenticates. (Follow-on: token via env var.)

### Assertion conventions (TABLES.md matcher DSL)
- Given: `a stub /cli server that replies with frames:` + table (literal canned replies).
- Then: `the stub server received frames:` + matcher table (what the proxy SENT — subsequence-ordered; `start`/`stdin`/`stdin-close` rows, `argv`/`data` matchers).
- Plus standard steps: `isaac is run with`, `stdin is: """..."""` (from acp cli-resume), `the stdout/stderr contains`, `the exit code is`. `${stub.url}` wired by the stub step.

### Scenarios (LOCKED, M1/M2 isolated)
1. ships argv, renders stdout, exits with the server's code
2. no command -> usage from the server (sends start with `[]`)
3. stdout/stderr render to their own local streams; nonzero exit relayed
4. local stdin forwarded as `stdin` frames, then `stdin-close` on EOF
5. `--token` sent as the bearer credential

### Left for later (NOT isolated proxy scenarios)
- M3 reconnect on transient drop; `--token` via env var.
- End-to-end interactive `isaac remote .../cli acp` -> the isaac-ec9q integration feature.

New-territory: revisit as implementation reveals constraints.
