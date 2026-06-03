---
# isaac-uhvt
title: MCP tool support
status: draft
type: epic
priority: low
created_at: 2026-06-03T06:50:37Z
updated_at: 2026-06-03T06:50:37Z
---

## Description

Add Model Context Protocol (MCP) client support so Isaac can discover and invoke third-party tools declared in config instead of requiring native per-tool integrations.

## Goals

- Support config-declared MCP servers for third-party tool integration
- Discover MCP tools and expose them through Isaac's existing tool registry
- Let crews opt into discovered tools through the existing allowlist model
- Route tool execution/results through the normal Isaac tool loop and transcript flow
- Preserve Isaac's operational guardrails: explicit config, explicit allow, timeouts, logging, and bounded output

## Scope

Initial slice should focus on MCP tools, not the full MCP surface.

- In scope:
  - MCP client transport and lifecycle for configured servers
  - Tool discovery
  - Tool execution
  - Config schema for server declarations
  - Naming/collision policy between MCP tools and existing tools
  - Error handling and timeout behavior
- Out of scope for the first bean:
  - MCP resources
  - MCP prompts
  - Generic remote auth UX beyond what a specific configured server needs

## Acceptance Criteria

- Isaac config can declare one or more MCP servers and validate their required fields
- Isaac can connect to a configured MCP server and discover its tools
- Discovered MCP tools can be exposed to the LLM using the existing tool-definition path
- A crew can allow an MCP tool and successfully invoke it in a turn
- MCP tool results are recorded and surfaced like other tool results
- Tool failures, server unavailability, and timeouts are returned as normal tool errors
- Name collisions between built-in/module tools and MCP tools have a defined policy and test coverage

## Notes

This should likely build on the existing config-driven module/tool architecture rather than invent a parallel tool system. Raw LSP support may be better served through MCP than through Isaac owning language-server protocol details directly.

Because this bean has no approved feature scenarios yet, it should remain draft until /plan-with-features produces committed @wip scenarios.
