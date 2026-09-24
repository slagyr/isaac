---
# isaac-1q9m
title: 'isaac-claude-code: drop the vestigial `isaac mcp-bridge` CLI command'
status: in-progress
type: task
priority: normal
created_at: 2026-09-24T17:58:50Z
updated_at: 2026-09-24T17:58:50Z
---

Micah, 2026-09-24: "Why does Zane still have an MCP bridge command? … definitely drop the command."

The driver never invokes it: `write-mcp-config!` launches the bridge as `bb -cp <process classpath> -m isaac.mcp-bridge.main --turn ID --url URL`, straight by namespace. The `:isaac/cli {:mcp-bridge …}` entry in `src/isaac-manifest.edn` only puts an internal per-turn plumbing command into every operator's `isaac` command list (and carries the inert `:hosted true` marker isaac-mfcd is retiring).

## Acceptance
- [ ] `:isaac/cli` entry removed from the claude-code manifest; `isaac help` on a host with the module no longer lists `mcp-bridge` (scenario in the module's CLI/bridge feature).
- [ ] The namespace and its spec stay for now (the transport bean deletes them); `write-mcp-config!` unchanged here.
- [ ] `bb ci` green; manifest patch bump.

Repo scope: isaac-claude-code.
