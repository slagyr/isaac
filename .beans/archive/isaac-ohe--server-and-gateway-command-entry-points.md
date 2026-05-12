---
# isaac-ohe
title: "Server and gateway command entry points"
status: completed
type: feature
priority: high
created_at: 2026-04-08T17:51:06Z
updated_at: 2026-04-08T18:09:57Z
---

## Description

Add CLI command behavior for starting the Isaac HTTP server.

## Scope
- Support  as the primary command
- Support  as a command alias for OpenClaw compatibility
- Support  config keys as an alias for 
- Add feature/spec coverage for startup success and startup logging
- Verify startup logs include host and port

## Notes
- This builds on the completed server skeleton work
-  is the preferred term;  remains a compatibility alias
- Feature file: features/server/command.feature

