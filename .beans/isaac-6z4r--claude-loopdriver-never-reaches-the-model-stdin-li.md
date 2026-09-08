---
# isaac-6z4r
title: 'claude LoopDriver never reaches the model: stdin lines lack the stream-json envelope and no --mcp-config is written'
status: completed
type: bug
priority: high
created_at: 2026-09-08T17:40:09Z
updated_at: 2026-09-08T18:00:52Z
parent: isaac-tuk1
---

Repo: isaac-claude-code (`src/isaac/llm/api/claude_cli.clj`: `conversation->stream-json`, `flag-args`/`build-argv`, `invoke!`). Child of isaac-tuk1; follow-up to isaac-0lyh (deployed 0.1.3: fallback now works, native loop still never runs).

## Evidence (zanebot, Claude Code 2.1.236, 2026-09-08 17:35Z, module 0.1.3 = 5b9d295)
- Field smoke: `:claude/driver-exit :exit-code 0 :events {} :result-event false :stderr nil` (~1 s spawn), then `:claude/driver-fallback :reason :cli-error`; the fence path answered `mcp-loop-ok`.
- Probe A (driver flags, proper stdin envelope, no mcp-config): streams normally, result event, exit 0.
- Probe B (same + `--strict-mcp-config --mcp-config` naming a server that fails to start): STILL streams normally; init reports `mcp_servers [{name isaac, status failed}]`. So MCP config does not silence the CLI.
- Probe C (driver flags with the driver's OWN stdin line `{"role":"user","content":"…"}`): exit 0, **0 bytes** stdout, empty stderr — the exact field symptom.
- Code: `conversation->stream-json` (claude_cli.clj:174) emits bare `{:role :content}` lines; the CLI's `--input-format stream-json` requires `{"type":"user","message":{"role":"user","content":…}}`. And `flag-args` adds `--strict-mcp-config` but nothing in the module ever writes an MCP config or passes `--mcp-config` (grep: no mcpServers / mcp-bridge / spit) — so even with correct stdin the driven turn would have no isaac tools.
- The fake Claude Code accepted both (it parses any JSON line; it never required --mcp-config), which is why 5xn7, nni3 and 0lyh were all green against it.

## Required
1. stdin: every line is a stream-json user envelope; prior turns are replayed as text inside user messages (assistant history as prose — stream-json input accepts only user messages); the live prompt is the last envelope.
2. Per driven turn: register the turn with the server's MCP turn registry (isaac-zocg: POST /mcp/turns/{id}), write a temp JSON `{"mcpServers":{"isaac":{"command":"isaac","args":["mcp-bridge","--turn","<id>", …]}}}` (plus whatever auth the bridge needs), pass `--mcp-config <file>`, delete the file after exit, unregister the turn.
3. The fake Claude Code enforces the real contract: a bare (non-envelope) stdin line → exit 0 with no output (so a regression reproduces the field failure in the suite); a driven invocation without --mcp-config → the fake refuses tool_use rows (no tools available).
4. Field check (verifier, via exec on zanebot after the train pins the new version): the smoke ends with `:claude/driver-exit :result-event true`, NO `:claude/driver-fallback`, `:turn/loop-driver :driver :provider`, and the transcript shows the toolCall/toolResult pair executed through the bridge.

## Scenarios (@wip, planted isaac-claude-code f93fa45 in features/llm/api/claude_driver.feature)
- stdin carries stream-json user envelopes, never bare role/content lines
- a driven turn writes an MCP config that points Claude Code at isaac's mcp-bridge for this turn

## Step ledger
| Step | Status |
|---|---|
| a fake Claude Code on the path scripted with: / the user sends / the response is / the fake Claude Code received on stdin: (now with type + message.* dotted columns) / the fake Claude Code was invoked with: | existing |
| **Then the fake Claude Code received no bare stdin lines** | **NEW** — the envelope rule is a negative constraint the table step cannot express |
| **Then the MCP config handed to the fake Claude Code names server {name} running:** (argv regex table) | **NEW** — reads the JSON file the fake was given via --mcp-config |

## Acceptance
- `bb features features/llm/api/claude_driver.feature` → all 11 scenarios green with @wip removed; `bb features && bb spec` green
- Field check as in Required 4, run by the verifier through its exec tool on zanebot AFTER the planner's train pins the released version (the verifier must not pass on 0.1.3): the planner records the deploy on this bean and re-hails verify.
- Module version bump (0.1.4) + registry pin (train step)

## Implementation notes

branch: bean/isaac-6z4r @ 2a3b652 (base origin/main@1937edc)

Driven stdin is now stream-json user envelopes (`{"type":"user","message":{…}}`); prior turns replay as prose inside a user message. Per driven spawn: register MCP turn, write temp `mcpServers.isaac` config running `isaac mcp-bridge --turn <id> --server <url>`, pass `--mcp-config`. Fake CLI: bare stdin → empty output; no `--mcp-config` → no tool_use. Module 0.1.4 (registry pin is train).

`bb features` 25/0/92; `bb spec` 45/0/136 (3 pending @real). Driver feature: 11 scenarios green, @wip removed. Field check is verifier-owned after train pins.



## Landed on main (2026-09-08)

main-sha: isaac-claude-code 2a3b65206f14301cbdd506fc620c73509a60978b

## Verify note (perceptor@isaac-verify, hail a05f9d5e)

Hermetic gates on origin/bean/isaac-6z4r @ 2a3b652:
- bb features features/llm/api/claude_driver.feature → 11 examples, 0 failures, 44 assertions
- bb features → 25 examples, 0 failures, 92 assertions
- bb spec → 45 examples, 0 failures, 136 assertions, 3 pending @real
- Feature: @wip removed on the two planted 6z4r scenarios; prior-turn stdin tables updated to type/message envelopes (authorized by the planted scenarios); 9 other driver scenarios otherwise unchanged
- Module version 0.1.4 on the landed commit. Registry pin + field check are train/planner (bean: "AFTER the planner's train pins the released version" / "verifier must not pass on 0.1.3"). Live smoke not run.\n
