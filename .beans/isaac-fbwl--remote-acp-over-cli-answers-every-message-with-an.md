---
# isaac-fbwl
title: Remote ACP over /cli answers every message with an extra {"id":null,"error":"Invalid Request"} — blank stdin lines reach the hosted acp; plus isaac.system shadows clojure.core/reset!
status: todo
type: bug
priority: normal
created_at: 2026-09-24T20:33:49Z
updated_at: 2026-09-24T20:33:49Z
---

Seen 2026-09-24 after cli-server c11fbdf went to zanebot (remote acp works again — no "Write end dead"). Driving `zane-isaac acp --crew marvin` (= `isaac remote wss://…/cli -- acp --crew marvin`) with `initialize` then `session/new` yields the correct responses AND, after each, `{"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid Request"}}`. The same messages piped into a LOCAL `isaac acp` produce no such line, so the blank line is introduced on the remote path: the proxy's stdin pump (`isaac.cli-proxy.proxy/pump-stdin!`, sends `(str (str/trim-newline line) "\n")` per line) and/or cli-server's frame-reader deliver an empty line between messages, and the ACP stdio loop answers a blank line with a JSON-RPC error instead of ignoring it. An ACP client (Toad) receives an unsolicited error response with a null id after every request.

Also on stderr at startup: `WARNING: reset! already refers to: #'clojure.core/reset! in namespace: isaac.system, being replaced by: #'isaac.system/reset!` — `isaac-acp/src/isaac/system.clj` defines `reset!` without `(:refer-clojure :exclude [reset!])`.

## Fix
- isaac-acp stdio loop: a blank/whitespace-only line is skipped, never answered (JSON-RPC has no request there). Spec + scenario: two messages separated by an empty line → exactly two responses.
- Find the blank-line source on the remote path (cli-proxy `pump-stdin!` frame per line vs cli-server `frame-reader`/stdin frames) and stop emitting empty frames; scenario in whichever repo owns it: a two-line stdin over /cli arrives at the hosted command as exactly two lines.
- `isaac.system` ns: `(:refer-clojure :exclude [reset!])`.

## Acceptance
- [ ] `zane-isaac acp --crew marvin` fed initialize + session/new prints exactly the two results and the session/update notification — no null-id errors; exit 0 on stdin EOF.
- [ ] No `WARNING: reset!` on stderr.
- [ ] `bb ci` green in the touched repos; version bumps; registry repins.

Repo scope: isaac-acp (`system.clj`, stdio loop), isaac-cli-proxy and/or isaac-cli-server (empty frames).
