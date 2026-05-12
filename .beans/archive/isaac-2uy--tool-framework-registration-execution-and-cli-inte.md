---
# isaac-2uy
title: "Tool framework - registration, execution, and CLI integration"
status: completed
type: task
priority: low
created_at: 2026-04-02T00:13:17Z
updated_at: 2026-04-07T23:58:01Z
---

## Description

Isaac has tool call loop implementations for Ollama and Anthropic, but no actual tools are registered or executable from the chat command.

## Scope
- Tool registry: define tools with name, description, parameters schema, and handler function
- Built-in tools: start with filesystem basics (read_file, list_dir, exec)
- Chat loop integration: when the LLM returns tool_calls, execute them and continue the loop
- Tool definitions sent in prompts based on agent config

## Depends on
- Provider dispatch (done)
- Anthropic tool calling format (done)
- Ollama tool calling format (done)

## Not yet
- Tool policies (allow/deny per agent)
- MCP tool support
- Dynamic tool loading from extensions

