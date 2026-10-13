---
# isaac-t2o2
title: 'isaac-agent: exec/run forwards named .env keys (tools.exec.forward-env)'
status: completed
type: feature
priority: normal
tags:
    - agent
created_at: 2026-10-13T17:40:33Z
updated_at: 2026-10-13T18:26:42Z
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
:tools {:exec {:forward-env ["VIMEO_TOKEN"]}}
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

- `:isaac.config/schema`: `:forward-env` (sequential strings) under the
  existing `:tools` key, as `:tools :exec :forward-env`.
- `isaac.agent.tool.exec/start-process` overlays the named values.
- Handbook: `.env` feeds `${VAR}` and named `tools.exec.forward-env`, not
  process `getenv`.
- Version bump.

On the skiff deployment after it lands: set `tools.exec.forward-env` to `["VIMEO_TOKEN"]`.

## Out of scope

Per-crew allowlists. Mutating the JVM environment. Forwarding into
MCP children or Claude (already has its own `:forward-env`). Teaching
crews to source `.env`.

## Acceptance

Scenarios and design approved by Micah 2026-10-13. Remove the three `@wip` tags and make them pass; `bb ci` and `bb jvm-spec` green; CI green on main.

Scenarios written 2026-10-13 as `@wip` in isaac-agent
`features/tool/exec_forward_env.feature` (no new steps):

1. A named key reaches the command, and an unnamed key does not. Fails today.
2. Nothing is forwarded unless it is named. Passes today; a guard.
3. A named key with no value (blank in `.env`, or absent) is left unset. Passes today; a guard.

"The process environment wins" has no scenario: a feature cannot set the
JVM's real environment. Cover it with a unit spec.

## Rulings (2026-10-13, Micah)

- **Key location:** `tools.exec.forward-env`, beside `tools.web_search`
  ("tools.exec.forward-env is better"). The title's `exec.forward-env` is
  the old name.

## Planner notes (2026-10-13)

- **Precedence already matches the Claude provider.** There the forwarded
  value comes from `isaac.foundation.config.env/env`, which prefers the
  process environment over `.env`, and is merged over the inherited
  environment. "The process environment wins" is the same behavior. Claude
  also has a literal `:env` overlay; exec has none and needs none here.
- **Instance-wide is recommended.** Per-crew forwarding would look like
  protection it cannot give: exec has no filesystem allow list, so any crew
  with exec can read `.env` whatever is forwarded. The real boundary would
  be a sandbox on exec, which is its own work.

feature-baseline: isaac-agent baed9c439f3142f4429267b39bdd985025a22a33
feature-blob: isaac-agent features/tool/exec_forward_env.feature b4b8fb4d318fffa3790cd8d24cbb66d99d9965a6

## Landed on main (2026-10-13)

main-sha: isaac-agent 32c56ce2885d65eac4f279df7ab8ac9d9fce9b3c

Acceptance: three scenarios green (6 assertions); bb ci green (1958 specs, 931 feature examples, 1 pre-existing pending); bb jvm-spec green (1958 specs). Gate PASS on the squash commit.
