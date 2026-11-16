---
# isaac-2z7z
title: ACP answers end of input with an Invalid Request error (id null) over remote /cli
status: draft
type: bug
priority: low
tags:
    - acp
created_at: 2026-11-16T18:30:01Z
updated_at: 2026-11-16T18:30:01Z
---

Repos: **isaac-acp** (stdio JSON-RPC loop) and/or **isaac-cli-server** (stdin-close handling) — find which side emits it.

## Observed (Micah's Mac → zanebot, 2026-11-16)

```bash
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize",...}' | isaac remote zane -- acp --crew zane
```

answers `initialize` correctly, then, when stdin closes, emits

```
{"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"Invalid Request"}}
```

Same with no trailing newline, and the same through the old pre-hu1n wrapper (`isaac remote wss://…/cli -- acp`), so it isn't new. Something turns end-of-input (or the proxy's stdin-close frame) into an empty request that the ACP loop rejects.

## Fix

End of input closes the ACP session quietly: no response for a non-request, no error with `id: null`. Blank lines are skipped too (JSON-RPC: a notification or non-request gets no response).

## Acceptance

Draft — scenario to come: ACP over stdio given one `initialize` line then EOF writes exactly one response (id 1) and exits 0; same through the remote /cli path.
