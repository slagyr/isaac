---
# isaac-h34o
title: Expose resolved commands over ACP (available-commands advertisement)
status: todo
type: feature
priority: normal
created_at: 2026-05-26T04:21:33Z
updated_at: 2026-05-26T16:21:28Z
parent: isaac-nwj3
blocked_by:
    - isaac-8qd5
    - isaac-dbg1
---

Deferred from isaac-nwj3. Expose the resolved command set over ACP (available-commands advertisement) so ACP clients (Zed, etc.) can list/invoke commands with CLI parity.

Enumerate resolved commands (global UNION project, precedence) from the registry; advertise name + params/usage (prompt-template commands take args, so convey argument hints). Refresh when the catalog changes.

Parent: isaac-nwj3. Builds on the discovery/registry.


## Cross-repo: lives in isaac-acp

ACP is the `../isaac-acp` sibling repo. The advertisement plumbing already exists there: `available-commands-notification` -> `available_commands_update`, sourced from `slash-registry/all-commands` (server.clj:20). So h34o = config-defined prompt-template commands appear in that list with **name + description + argument hint** (params -> `input.hint`), and ACP surfaces them for free.

**Feature file (in isaac-acp):** `features/comm/acp/slash_commands.feature` — added an `@wip` scenario: a config command (`work`, `params: [bean]`) is advertised via `available_commands_update` with its description and `input.hint = bean` (sorts in after the builtins status/model/crew/cwd/effort at index 5). Committed in isaac-acp (b6c9bbb), not yet pushed.

**Definition of done:** remove `@wip` in isaac-acp and the scenario is green.

**Scope:** mostly main-repo (register config commands into `slash-registry/all-commands` with description + a params-derived hint — overlaps isaac-dbg1) + an isaac-acp transform mapping the command's params to ACP `input.hint` if needed.

Cross-repo (isaac-acp). Blocked by isaac-8qd5 (catalog) and effectively isaac-dbg1 (command registration).
