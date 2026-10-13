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

Micah, 2026-10-13: a crew on the skiff deployment has `VIMEO_TOKEN` in
`~/.isaac/.env`, but `exec/run` does not see it. `.env` is a snapshot for `${VAR}` substitution
in config (`lock-dotenv!`). It is not copied into the Unix environment
that `ProcessBuilder` inherits. A restart re-locks `.env` for config and
still leaves `printenv VIMEO_TOKEN` empty.

The crew can source `.env` inside `exec/run` (that tool has no filesystem
allow list). That loads every secret in the file, and the crew has been
told not to open `.env`. Named forwarding is the intended path.

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

On the skiff deployment after it lands: set `exec.forward-env` to `["VIMEO_TOKEN"]`.

## Out of scope

Per-crew allowlists. Mutating the JVM environment. Forwarding into
MCP children or Claude (already has its own `:forward-env`). Teaching
crews to source `.env`.

## Acceptance

Draft until Micah approves the scenarios; not yet baselined.

Scenarios written 2026-10-13 as `@wip` in isaac-agent
`features/tool/exec_forward_env.feature` (no new steps):

1. A named key reaches the command, and an unnamed key does not. Fails today.
2. Nothing is forwarded unless it is named. Passes today; a guard.
3. A named key with no value (blank in `.env`, or absent) is left unset. Passes today; a guard.

"The process environment wins" has no scenario: a feature cannot set the
JVM's real environment. Cover it with a unit spec.

## Open questions (planner review, 2026-10-13)

- **This does not protect the secret.** exec has no filesystem allow list,
  so any crew with exec can already read `.env`; forwarding is a
  convenience. A forwarded key is readable by every crew with exec, and so
  by anyone who can ask such a crew to print it. Per-crew forwarding
  (`defaults.crew.tools`, overridable per crew) would narrow that.
- **Key location.** Per-tool config already lives under `:tools`
  (`tools.web_search`); `tools.exec.forward-env` would match. The design
  line and the scenarios say top-level `:exec`.
- **Precedence** differs from the Claude provider's `forward-env`, where an
  explicit `env` overlay wins over the forwarded value. Say why exec differs,
  or match it.
