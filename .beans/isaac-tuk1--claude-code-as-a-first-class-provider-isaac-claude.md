---
# isaac-tuk1
title: 'Claude Code as a first-class provider: isaac-claude-code module + native tool loop via MCP'
status: completed
type: feature
priority: normal
tags:
    - claude-cli
    - epic
created_at: 2026-09-03T23:07:34Z
updated_at: 2026-09-09T00:13:48Z
---

Micah (2026-09-03): the claude subscription is a large, under-used token budget; using Claude Code within its terms of use is worth a real investment. Two problems today: (1) the claude-cli provider drives tools through a textual fence protocol that opus silently drifts out of (isaac-jkx7), and (2) the provider lives in isaac-agent core. Spike isaac-khgy proved the alternative: keep the CLI's tools off, hand it isaac's tools as an MCP server, let Claude Code run the native tool loop, and read the whole turn back from its stream-json event feed (assistant blocks, tool pairs by id, per-message usage). Decisions: extract the provider into its own module FIRST as a pure move (repo created: https://github.com/slagyr/isaac-claude-code), then add the core seams (loop driver, per-turn tool registry + local MCP endpoint + stdio bridge), then build the driver in the module. Chronicles and episodes are untouched — they sit outside the tool-loop seam. Children in order: extraction → loop-driver seam → tool registry/MCP bridge → driver v2 (+ fake-CLI harness). isaac-jkx7 becomes the hardening of the legacy fence path (still needed for older CLIs / other CLI-backed providers).



## Summary of Changes (2026-09-09)
Claude Code is a first-class provider: module isaac-claude-code (`:isaac.llm.claude`, 0.1.9 live on zanebot) with the :claude-cli llm-api factory; isaac-agent LoopDriver seam (1sdl); per-turn MCP registry + server route + `isaac mcp-bridge` (zocg, kbu0: process-global registry); the driver (5xn7) hardened against the real CLI across nine field layers (nni3 --verbose, 0lyh stream shapes + driver-exit log + error fallback, 6z4r stdin envelopes + --mcp-config, lrvb no fence on driven turns + mcp-status, o2fh server URL/token + pending≠failed + JSON-RPC unauthorized, 1tmw once-only dispatch + one spawn + reply once, 8slm aside≠reply, g2z8 one text source). Field-proven 2026-09-09: a server-origin turn (smoke band, session pinned to claude-cli) runs the native loop — one spawn, bridge tools/list + tools/call, one toolCall/toolResult pair, exact reply, no fallback. Known by design: turns started through the remote CLI run out of process and use the fence fallback (kbu0). isaac-jkx7 (fence-path hardening for opus drift) detached as a standalone draft.
