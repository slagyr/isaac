---
# isaac-quji
title: 'isaac-agent: slash-command berth becomes providers that can reply or start a turn'
status: draft
type: feature
priority: normal
created_at: 2026-10-08T20:38:14Z
updated_at: 2026-10-08T20:38:14Z
parent: isaac-pcm3
---

DRAFT. Needs scenarios before it is todo. Part of the prompts-and-habits epic.

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
