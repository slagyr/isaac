---
# isaac-7r5
title: "Flatten tool success result shape"
status: completed
type: bug
priority: high
created_at: 2026-04-08T21:12:00Z
updated_at: 2026-04-08T21:23:27Z
---

## Description

Tool handlers and the registry should agree on one normalized result contract. Successful tool executions currently risk nested success payloads such as {:result {:result ...}} because built-in tools already return normalized maps and the registry wraps success again.

## Scope
- Define the canonical tool result contract as:
  - success: {:result ...}
  - error: {:isError true :error ...}
- Remove extra success wrapping in registry execution
- Preserve normalization for unknown tools and thrown exceptions
- Add feature/spec coverage so successful tool results are flat and never nested
- Keep camelCase argument keys for tool schemas and calls

## Notes
- Built-in tools already use camelCase keys such as filePath, oldString, newString, replaceAll
- The tool registry should pass through normalized success maps unchanged

