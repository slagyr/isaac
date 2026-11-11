---
# isaac-y3wm
title: 'claude-code: spawn failures are reported as mcp-unavailable'
status: todo
type: bug
priority: high
created_at: 2026-11-11T15:08:00Z
updated_at: 2026-11-11T15:08:00Z
---

## Problem

The claude-code provider labels every exception in a driven turn as
`:mcp-unavailable`. In `claude_cli.clj` `invoke!`, the outer `catch` maps any
`Exception` to `mcp-init-weather`, and so does the `register-mcp-turn!` guard.
A failed launch of the `claude` program therefore looks like an MCP outage.

Seen live on 2026-11-11: a JDK package upgrade under a running server broke
process spawning (`Cannot run program "claude": Failed to exec spawn helper …
JDK version mismatch`). For 8.5 hours every turn parked as
`suspended (mcp-unavailable)`, and the attention channel repeated that label
every half hour. The real error appeared only in unrelated
`episodes/seal-failed` warnings. Nothing on the turn path logged it.

## Goal

A turn's weather names what actually failed, and the cause is recorded where
an operator looks: the turn marker, the server log, and the attention notice.

## Design (planner, 2026-11-11, Micah delegated the scenarios)

- New weather reason **`:launch-failed`**: the provider's process could not be
  started (an exception from the launch itself, e.g. `IOException` "Cannot run
  program"). It's still weather: the turn parks and the sweep retries, so a
  restart heals it without losing the turn. The name is generic on purpose, so
  any process-backed provider can use it.
- `:mcp-unavailable` stays for real MCP trouble: `register-mcp-turn!` /
  listener start failing, the CLI's init event reporting the `isaac` server
  failed, and the existing fake "fails MCP initialization" path. The existing
  isaac-izc1 scenarios guard that.
- The weather `:message` is stored on the turn marker (today the marker has
  no message, which is why the 11-11 park was so hard to diagnose).
- Every weather result from the driven path logs `:claude/turn-weather` at
  `:warn` with `:reason` and `:message`.
- The parked-turn attention text includes the marker's message. `:launch-failed`
  escalates on the same rule as `:mcp-unavailable` (2nd park once
  `:turn :suspended-attention-ms` has passed).

## Acceptance

Scenarios (isaac-claude-code, `@wip` on main at c5cf121):

- `features/llm/api/claude_driver.feature:699` — a claude command that cannot be started parks as launch-failed, with the cause on the marker and in the log
- `features/llm/api/claude_driver.feature:722` — a repeated launch failure escalates to an attention notice that names the cause

Run from isaac-claude-code (after removing `@wip`):

```sh
clojure -M:features features/llm/api/claude_driver.feature:699
clojure -M:features features/llm/api/claude_driver.feature:722
bb ci
```

New step to implement (isaac-claude-code steps):
`the fake Claude Code fails to start with {text:string}`. The fake must throw a
`java.io.IOException` with that message from the process-launch site (where
`process/process` would throw), not return an exit result. The rest of the
fake's script is unused.

The existing isaac-izc1 `:mcp-unavailable` scenarios in the same file must
stay green.

## Likely repo scope

- isaac-claude-code: `api/claude_cli.clj` `invoke!` (classify launch exceptions,
  log `:claude/turn-weather`), the new fake step.
- isaac-agent: `drive/weather.clj` (`weather-reason` learns `:launch-failed`,
  the marker keeps `:message`, the escalation rule covers `:launch-failed`),
  `attention.clj` `parked-content` (append the message). Turn-marker schema, if
  one conforms the marker. Bump the isaac-agent pin in isaac-claude-code once
  the agent change lands.

## Related

isaac-u1oe — the server survives a JDK upgrade on disk (prevents the underlying spawn failure).

feature-baseline: isaac-claude-code c5cf1213efc0f60f01b67e71bbfa0569dd285d09
feature-blob: isaac-claude-code features/llm/api/claude_driver.feature 5117b072e42bad6dc2dd460037e8a6bfe88d1ac3 699,722
