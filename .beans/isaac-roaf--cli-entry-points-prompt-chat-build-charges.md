---
# isaac-roaf
title: CLI entry points (prompt, chat) build charges
status: in-progress
type: task
priority: normal
created_at: 2026-05-21T00:22:23Z
updated_at: 2026-05-21T18:01:30Z
parent: isaac-895
blocked_by:
    - isaac-a9y0
---

## Scope (child of isaac-895; depends on isaac-a9y0)

The `isaac prompt` and `isaac chat` CLI subcommands also synthesize
turn requests today. After isaac-a9y0 lands, they should build a charge
via `charge/build` and dispatch via `bridge/dispatch!` directly.

## Proposed change

- `src/isaac/bridge/prompt_cli.clj` — `isaac prompt <input>` builds a
  charge from CLI args + config defaults and dispatches.
- `src/isaac/bridge/chat_cli.clj` — `isaac chat` builds a charge per
  turn from terminal input and dispatches.

## Safety net

- `features/cli/chat.feature` (after the screaming-arch move).
- `features/bridge/cli-prompt.feature` (after the screaming-arch move).

All existing CLI feature scenarios must pass without modification.

## Acceptance

- Neither `prompt_cli` nor `chat_cli` constructs a "request" map.
- Both call `charge/build` and `bridge/dispatch!` directly.
- All existing CLI feature scenarios pass.
- The word "request" does not appear in `bridge/prompt_cli.clj` or
  `bridge/chat_cli.clj`.



## Verification failed

HEAD: d2a727b891f4d11b2fb2efc7cb8a1a43a46c082d
Working tree: clean

The bean acceptance explicitly covers both `prompt` and `chat`, but current `main` only updates `prompt`. There is no `chat` CLI entry point left on `main`, no `src/isaac/bridge/chat_cli.clj`, and no surviving chat feature/spec safety net. If chat was intentionally removed as part of the larger refactor, the bean acceptance needs to be rewritten; as written, the implementation is incomplete against its stated scope.
