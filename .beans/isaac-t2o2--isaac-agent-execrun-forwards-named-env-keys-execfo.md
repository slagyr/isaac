---
# isaac-t2o2
title: 'isaac-agent: exec/run forwards named .env keys (exec.forward-env)'
status: draft
type: feature
priority: normal
tags:
    - agent
created_at: 2026-10-13T17:40:33Z
updated_at: 2026-10-13T17:40:33Z
---

## Why

Micah, 2026-10-13: Yopp has `VIMEO_TOKEN` in `/home/yopp/.isaac/.env`, but
`exec/run` does not see it. `.env` is a snapshot for `${VAR}` substitution
in config (`lock-dotenv!`). It is not copied into the Unix environment
that `ProcessBuilder` inherits. A restart re-locks `.env` for config and
still leaves `printenv VIMEO_TOKEN` empty.

Yopp can source `.env` inside `exec/run` (that tool has no filesystem
allow list). That loads every secret in the file, and he has been told
not to open `.env`. Named forwarding is the intended path.

## Design (2026-10-13, Micah)

Instance-wide, same shape as Claude's `:forward-env`:

```
:exec {:forward-env ["VIMEO_TOKEN"]}
```

- Default is an empty list. A secret is forwarded only when named.
- At spawn, `exec/run` copies those names from
  `isaac.foundation.config.env/env` into the child
  `ProcessBuilder.environment()`.
- If the name is already in the process environment, that value wins.
- A missing or blank value is omitted.
- Adding a key to `.env` still needs a config reload or restart
  (`lock-dotenv!`). Adding a name to `forward-env` hot-reloads.
- Do not inject the whole `.env`. Do not mutate `System/getenv`.
- Do not restore systemd `EnvironmentFile`.

## Implementation sketch

isaac-agent:

- `:isaac.config/schema` key `:exec` with `:forward-env` (sequential
  strings).
- `isaac.agent.tool.exec/start-process` overlays the named values.
- Handbook: `.env` feeds `${VAR}` and named `exec.forward-env`, not
  process `getenv`.
- Version bump.

On Yopp after it lands: set `exec.forward-env` to `["VIMEO_TOKEN"]`.

## Out of scope

Per-crew allowlists. Mutating the JVM environment. Forwarding into
MCP children or Claude (already has its own `:forward-env`). Teaching
crews to source `.env`.

## Acceptance

Draft. Scenarios after the scenario plan is approved.

Likely home: isaac-agent `features/tool/exec_forward_env.feature`.
Reuse `the isaac .env file contains:` (foundation) and `exec__run`
from `features/tool/built_in.feature`.
