---
# isaac-fstx
title: 'Namespace restructure: isaac-gchat under its module id'
status: todo
type: task
priority: normal
created_at: 2026-09-30T14:12:34Z
updated_at: 2026-09-30T14:12:34Z
parent: isaac-vyqs
blocked_by:
    - isaac-on0o
    - isaac-tacl
---

Micah, 2026-09-30. **A module's code lives under its module id.** isaac-foundation → `isaac.foundation.*`, isaac-agent → `isaac.agent.*` (e.g. `isaac.session.frequencies` → `isaac.agent.frequencies`), isaac-claude-code (`:isaac.provider.claude-code`) → `isaac.provider.claude-code.*`, isaac-episodes (`:isaac.session.episodes`) → `isaac.session.episodes.*`, comm modules → `isaac.comm.<name>.*`, and so on. Source, specs, spec-support, step namespaces, manifest symbols (`:factory`, berth entries), bb tasks and docs all move together. Clean cutover: no alias namespaces.

## Order: inside-out (Micah)

1. isaac-foundation (requires nobody).
2. isaac-agent: bump to the new foundation, update its foundation requires, rename its own.
3. Every other module, in parallel: bump foundation + agent (this absorbs the pin sweep isaac-5x21), update requires, rename its own. A module that requires another leaf (gchat/gmail → google) goes after that leaf.

Each repo is touched once.

## Deploy freeze

An installed Isaac runs one foundation and one agent, so zanebot/yopp don't take the new foundation until every installed module has migrated. Each repo's main stays green on its own pins meanwhile.

## Every child bean also

- greps zanebot and yopp live config (read-only, `ssh zane@zanebot…` / `ssh yopp@yopp`) for namespace names in data (hook `:factory`, embedding `:namespace`, etc.) and lists required config edits in the bean;
- updates its handbook chapter and README where namespaces are named;
- is ungated (mechanical refactor): acceptance = full CI green on main, a grep showing no namespaces outside the module's id prefix in src/spec (list justified exceptions), planner verification.
