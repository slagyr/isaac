---
# isaac-2z7z
title: ACP answers end of input with an Invalid Request error (id null) over remote /cli
status: in-progress
type: bug
priority: low
tags:
    - acp
created_at: 2026-11-16T18:30:01Z
updated_at: 2026-11-16T18:36:08Z
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

## Cause (planner, 2026-11-16)

Not EOF: **blank lines.** The proxy (`isaac-cli-proxy` `pump-stdin!`) sends each line as `line + "\n"`, and `isaac-cli-server` `-send-stdin!` appends another `"\n"`, so every line reaches the hosted command followed by a blank line. The ACP stdio loop answers a blank line with `-32600 Invalid Request`, `id: null`. Reproduced over plain stdio. This bean fixes the ACP side (skip blank lines; JSON-RPC sends no response to a non-request). The doubled newline in cli-server is its own bean.

## Acceptance

Scenario in `isaac-acp/features/comm/acp/cli.feature` (scenario-level `@wip`; remove it when it passes):

- `bb features features/comm/acp/cli.feature:257` — a blank line in the ACP input gets no response: both real requests answer, no `Invalid Request`, exit 0

feature-baseline: isaac-acp dd216128ed44c2567abbbceaf399024f4ae202fd
feature-blob: isaac-acp features/comm/acp/cli.feature a69a8ffca6a7e8c4704429c6359f2da034d9c048 257
