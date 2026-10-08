---
# isaac-quji
title: 'isaac-agent: slash-command berth becomes providers that can reply or start a turn'
status: completed
type: feature
priority: normal
created_at: 2026-10-08T20:38:14Z
updated_at: 2026-10-08T21:52:57Z
parent: isaac-pcm3
---

Part of the prompts-and-habits epic (isaac-pcm3). Design and scenarios approved by Micah 2026-10-08.

## Problem

`:isaac.agent/slash-commands` takes a fixed command name plus a factory in a manifest, and a handler can only return a reply. Prompt commands fit neither half: they are discovered from files at runtime and vary by working directory, and they expand into a new input that starts a turn.

So the bridge hard-codes them. `isaac.agent.bridge.core/handle-slash` tries the registry and, on a miss, calls `prompt-catalog/resolve-command-prompt` and runs a turn. `isaac.agent.slash.registry` likewise calls the catalog itself to add prompt commands to the advertised list.

## Who uses the berth today

Only the agent: its five built-ins (`/crew`, `/cwd`, `/effort`, `/model`, `/status`) and the test fixture module `isaac.slash.echo`. No shipped module contributes a slash command, so reshaping the berth breaks nobody.

## Proposal

Reshape the berth into providers instead of adding a second berth beside it. A provider:

- given a name, args and the session's context, returns nothing, a reply, or an expanded input to run as a turn;
- lists the commands it can offer for a given context, for advertisement.

The built-ins become one provider. Prompt commands become another, in-agent at first. The bridge loses its catalog fallback and the registry loses its catalog import. Same shape as the existing `:isaac.agent/tool-providers` berth.

## To settle

- Order between providers when two claim a name. Today a registered command wins over a prompt command of the same name; keep that.
- The autonomous-origin rule in `handle-slash` (an unknown `/name` from an autonomous origin runs as a plain turn) stays in the bridge.

## Likely repo scope

`isaac-agent`.

## Approved design (Micah, 2026-10-08)

- The berth keeps its name, `:isaac.agent/slash-commands`. An entry is now a provider: `{<provider-id> {:factory <sym> :rank <int, optional>}}`.
- A provider does two things: it lists the commands it offers for a session (`name`, `description`, optional `rank`), and it answers one by name with either a reply (no turn) or an input that the bridge runs as a turn.
- The built-ins are one provider. Prompt-template commands are another, in-agent for now. `handle-slash` loses its direct call to the prompt catalog and `slash/registry.clj` loses its catalog import.
- **Rank decides collisions; lower answers.** Nobody is forced to state one. Defaults: built-ins 100, a module's commands 500, prompt-template commands 900. A module may set `:rank` on its entry, or on a single command, to move it ahead of or behind the others.
- This replaces the old rule (a module declaring a built-in's name overrides it, last-wins with a warning). By default the built-in now wins. A provider's commands are only known at runtime, so there is no register-time collision warning.
- The autonomous-origin rule stays in the bridge: an unknown `/name` from an autonomous origin still runs as a plain turn.

## Scenarios (committed `@wip` on isaac-agent main, `features/module/slash_provider.feature`)

1. a provider's command can start a turn with its own input (line 35)
2. a provider's command can reply without starting a turn (line 45)
3. a provider's commands are advertised alongside the built-ins (line 49)
4. a built-in outranks a module's command of the same name by default (line 57)
5. a module's command outranks a prompt-template command of the same name by default (line 62)
6. a command given a lower rank outranks a built-in (line 80)
7. a command given a higher rank yields to a prompt-template command (line 84)

## Step ledger

| Step | Status |
|---|---|
| `default Grover setup` | existing |
| `the isaac EDN file "…" exists with:` | existing |
| `the isaac file "…" exists with:` | existing |
| `the following sessions exist:` | existing |
| `the following model responses are queued:` | existing |
| `the user sends "…" on session "…"` | existing |
| `session "…" has transcript matching:` | existing |
| `the reply contains "…"` | existing |
| `the reply does not contain "…"` | existing |
| `the available slash commands include:` | existing |

No new steps. New test code is one fixture module, `isaac-agent/modules/isaac.slash.semaphore`, whose commands are listed in the feature header. The existing `isaac.slash.echo` fixture becomes a provider offering `/echo`.

## Acceptance

- `@wip` is removed from `features/module/slash_provider.feature` and all seven scenarios pass.
- No behavior change elsewhere: these features pass with no edits to their scenarios: `features/module/slash_extension.feature`, `features/prompts/commands.feature`, `features/bridge/commands.feature`, `features/bridge/crew.feature`, `features/bridge/model.feature`.
- `isaac.agent.bridge.core` and `isaac.agent.slash.registry` no longer require `isaac.agent.prompt.catalog`.
- The registry spec's last-wins-with-warning case is replaced by rank cases.
- The berth description in `resources/isaac-manifest.edn` and the agent handbook chapter describe providers and rank.

```
cd isaac-agent && bb features features/module/slash_provider.feature && bb features features/module/slash_extension.feature features/prompts/commands.feature features/bridge && bb ci && bb jvm-spec
```


feature-baseline: isaac-agent 24ce347a040269e0d24a74947b33944c03317fe3
feature-blob: isaac-agent features/module/slash_provider.feature eff8770f7ea0bc0d2ff84fe68c346b142220b908

## Landed on main (2026-10-08)

main-sha: isaac-agent 2311a74558362776acc46534f3e46aa4f3897de6
