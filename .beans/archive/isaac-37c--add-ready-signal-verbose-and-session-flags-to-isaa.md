---
# isaac-37c
title: "Add ready signal, --verbose, and --session flags to isaac acp"
status: completed
type: feature
priority: low
created_at: 2026-04-12T00:53:21Z
updated_at: 2026-04-12T01:02:55Z
---

## Description

The isaac acp command currently starts silently and has no options. Add three things:

## 1. Ready signal to stderr
On startup, print 'isaac acp ready' to stderr so clients (or humans watching the subprocess) know the loop is live. stdout stays sacred — every byte is part of the JSON-RPC protocol.

## 2. --verbose flag
Enable debug-level logging to stderr. For development/debugging when a client says 'nothing happened' — you can pipe stderr and see the inbound method names, dispatch results, errors. Today there's zero visibility into the loop.

At minimum, verbose should log inbound method names when requests arrive.

## 3. --session <key> flag
Attach the command to an existing session at startup. Behavior:
- If the session exists: subsequent session/new requests return the attached key instead of generating a fresh one. Other ACP protocol methods work as usual.
- If the session does NOT exist: fail immediately. Print 'session not found: <key>' to stderr and exit 1. Do not silently create it.

cwd is always inherited from the process — unchanged by --session.

## Implementation

- src/isaac/cli/acp.clj — add flag parsing for --verbose and --session
- On startup, if --session is set, verify the session exists via storage. Fail if missing.
- If attached, override the session/new handler so it returns the attached key.
- If --verbose, install a wrapper around rpc/dispatch that logs the method name to stderr.
- Always print the ready signal to stderr before entering the read loop.

## New step definition

'the stderr contains {string}' — mirrors 'the output contains' but against captured stderr. The existing 'isaac is run with' step needs to capture stderr separately in addition to stdout.

## Feature scenarios (all in features/cli/acp.feature)

- @wip 'acp command prints a ready signal to stderr on startup' (line 53)
- @wip '--verbose enables debug logging to stderr' (line 60)
- @wip '--session attaches the acp command to an existing session' (line 70)
- @wip '--session fails if the session does not exist' (line 90)

## Acceptance

Remove @wip from all 4 scenarios and verify each passes:
  bb features features/cli/acp.feature:53
  bb features features/cli/acp.feature:60
  bb features features/cli/acp.feature:70
  bb features features/cli/acp.feature:90

Manual verification:
  bb isaac acp 2>&1 >/dev/null   # should print 'isaac acp ready' and block
  bb isaac acp --verbose         # pipe some JSON-RPC through stdin and watch stderr
  bb isaac acp --session agent:main:acp:direct:nonexistent   # should exit 1 with error

Full suite: bb features and bb spec pass.

