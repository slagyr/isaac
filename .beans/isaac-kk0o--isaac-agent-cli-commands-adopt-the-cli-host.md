---
# isaac-kk0o
title: isaac-agent CLI commands adopt the CLI host
status: completed
type: feature
priority: high
tags:
    - cli
created_at: 2026-09-18T01:38:40Z
updated_at: 2026-09-18T16:51:43Z
parent: isaac-eqkb
---

Split from isaac-1fwl (child 2 of isaac-eqkb). Agent-only CLI host adoption.

isaac-1fwl landed on foundation `cc53d69` (`ensure-runtime! {:install! …}` + `:hosted` passthrough). Bump this repo's foundation pin to that SHA or later before compile. Do **not** land foundation.

## Commands

| command | change |
|---|---|
| `sessions` | `install-cli!` → `host/ensure-runtime!`; **delete `config/dangerously-install-config! nil` in the `finally`s** (session/cli.clj show + mutation); `builtin/register-all!` moves behind ensure-runtime! |
| `prompt` | install/runtime/register-all! → ensure-runtime!; `user.dir` → `host/cwd` |
| `auth`, `crew`, `turns` | `load-config!` → ensure-runtime!; `auth` read-line via host in; device-code poll checks `host/cancelled?` |

Each migrated command sets `:hosted true` on its `:isaac/cli` manifest entry.

Adopt foundation's `bb lint-cli-host` in this repo's `bb ci`.

## Fixture

`host/run-embedded` against the already-installed Grover runtime. No per-command re-install — a second cold `isaac is run with` would reload from disk and hide the `finally` nil.

New steps (isaac-agent):

- `the command is run embedded with argv {argv:string}`
- `the command is run embedded with argv {argv:string} and cwd {cwd:string}`

## Acceptance

@wip `features/cli/host_embed.feature` (commit c1c61e2):

- `features/cli/host_embed.feature:9` — An embedded sessions show does not clear the live config
- `features/cli/host_embed.feature:19` — An embedded prompt records the supplied cwd

Identity/`identical?` specs for config, session store, and tool registry are worker tests, not the review contract.

```
cd isaac-agent && bb features features/cli/host_embed.feature && bb lint-cli-host && bb ci
```

Done when `@wip` is gone and those commands are green.

## Non-goals

Do **not** migrate acp or other modules. Do **not** land foundation. Pin bump is isaac-1fwl.

## Handoff (isaac-work-1, 2026-09-18)

branch: bean/isaac-kk0o @ 0f7b1e45eae203e3ae62d88b5d3b0fe609b6e9df (base origin/main@d4559157eebb94be644e4de59a94e1c6bfeada43)

Acceptance green:

- `bb features features/cli/host_embed.feature` — 2 examples, 0 failures, 5 assertions (`@wip` already gone)
- `bb lint-cli-host` — ok
- `bb ci` — 1635 spec examples 0 failures; 801 feature examples 0 failures (1 pending, pre-existing mid-turn compaction)

Embedded fixture registers builtin `:isaac/cli` berths once against the live Grover runtime (`session_steps.clj:894`) because `host/run-embedded` does not call `main/run`. No config re-install. Verify lands the branch — do not merge or pin.



## Landed on main (2026-09-18)

main-sha: isaac-agent b46ef7ab2c71b20904bfea085e3ddf31a733d603
