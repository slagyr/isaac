---
# isaac-tuk1
title: 'Claude Code as a first-class provider: isaac-claude module + native tool loop via MCP'
status: in-progress
type: epic
tags:
    - claude-cli
    - epic
created_at: 2026-09-03T23:07:34Z
updated_at: 2026-09-03T23:07:34Z
---

Micah (2026-09-03): the claude subscription is a large, under-used token budget; using Claude Code within its terms of use is worth a real investment. Two problems today: (1) the claude-cli provider drives tools through a textual fence protocol that opus silently drifts out of (isaac-jkx7), and (2) the provider lives in isaac-agent core. Spike isaac-khgy proved the alternative: keep the CLI's tools off, hand it isaac's tools as an MCP server, let Claude Code run the native tool loop, and read the whole turn back from its stream-json event feed (assistant blocks, tool pairs by id, per-message usage). Decisions: extract the provider into its own module FIRST as a pure move (repo created: https://github.com/slagyr/isaac-claude), then add the core seams (loop driver, per-turn tool registry + local MCP endpoint + stdio bridge), then build the driver in the module. Chronicles and episodes are untouched — they sit outside the tool-loop seam. Children in order: extraction → loop-driver seam → tool registry/MCP bridge → driver v2 (+ fake-CLI harness). isaac-jkx7 becomes the hardening of the legacy fence path (still needed for older CLIs / other CLI-backed providers).
