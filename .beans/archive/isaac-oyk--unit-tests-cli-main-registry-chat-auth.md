---
# isaac-oyk
title: "Unit tests: CLI (main, registry, chat, auth)"
status: completed
type: task
priority: normal
created_at: 2026-04-02T00:16:13Z
updated_at: 2026-04-02T00:31:52Z
---

## Description

spec/isaac/main_spec.clj, cli/registry_spec.clj, cli/chat_spec.clj, cli/auth_spec.clj are missing.

Cover:
- main: command dispatch, help, unknown command, alias resolution
- registry: register!, get-command, all-commands, command-help
- chat: prepare (model resolution, session creation/resumption, soul loading)
- auth: login/status/logout dispatch, option parsing

Use TDD skill. Follow speclj conventions.

