---
# isaac-6h1
title: "Register isaac acp CLI command that runs the stdio loop"
status: completed
type: bug
priority: high
created_at: 2026-04-12T00:25:52Z
updated_at: 2026-04-12T00:30:02Z
---

## Description

The ACP protocol handlers exist (isaac.acp.server with initialize, session/new, session/prompt, etc.) but there is no 'isaac acp' CLI command wrapping them into a stdin/stdout loop. Running 'isaac acp' today prints 'Unknown command: acp'.

This means:
- Toad can't launch Isaac as an ACP agent (isaac chat --toad spawns 'isaac acp' which doesn't exist)
- IntelliJ/Zed can't connect to Isaac as an ACP agent
- The whole ACP epic works in isolation (features/acp/*.feature scenarios pass via direct dispatch) but the command-line entry point was never built

## Scope

1. Create src/isaac/cli/acp.clj namespace
2. Register the command with isaac.cli.registry
3. Implement a run function that:
   - Reads lines from *in* (or System/in)
   - For each line, calls rpc/handle-line with the handlers from isaac.acp.server/handlers
   - Writes each response as a single JSON line to stdout and flushes
   - Writes any notifications in the response envelope as separate lines
   - Loops until EOF, then exits with code 0
4. Require isaac.cli.acp in isaac.main so the command registers on startup

## Generic stdio stubbing infrastructure

Add two new step definitions (in spec/isaac/features/steps/cli.clj or a new stdio.clj):

- 'stdin is:' — accepts a docstring, stores as :stdin-content in scenario state
- 'stdin is empty' — stores empty string as :stdin-content

The existing 'isaac is run with {string}' step in cli.clj needs to check for :stdin-content in scenario state and, when present, bind *in* to a StringReader wrapping that content before calling main/run. When absent, leave *in* alone (existing scenarios unaffected).

These stubs are generic — they can be reused by any future command that reads stdin.

## Feature file

features/cli/acp.feature (4 @wip scenarios):
- acp command is registered and has help
- acp command reads a request from stdin and writes a response to stdout
- acp command loops over multiple stdin requests
- acp command exits cleanly on stdin EOF

## Acceptance

Remove @wip from all 4 scenarios and verify each passes:
  bb features features/cli/acp.feature:18
  bb features features/cli/acp.feature:24
  bb features features/cli/acp.feature:37
  bb features features/cli/acp.feature:49

Manual verification: echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":1}}' | bb isaac acp
Should print a JSON response with id:1 and exit.

Full suite: bb features and bb spec pass.

