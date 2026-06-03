---
# isaac-h34o
title: Expose resolved commands over ACP (available-commands advertisement)
status: in-progress
type: feature
priority: normal
created_at: 2026-05-26T04:21:33Z
updated_at: 2026-06-03T06:10:46Z
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



## Verification failed

HEAD: 5c24201d35e3d1c532c2b560365933b53c8b9067
Working tree: clean

Acceptance check 1 failed. I found no top-level ## Exceptions section in the bean. The bean explicitly references ../isaac-acp/features/comm/acp/slash_commands.feature and says an @wip scenario was committed there in isaac-acp commit b6c9bbb, but the current sibling checkout at f554ccf640e388c1b4d00710d62e2a582ac5cf42 does not contain that commit and the feature file does not contain the advertised config-command scenario at all. Current git log for that feature only shows 316464e, and the file currently has only the base status-command scenarios. Because the cross-repo spec artifact required by the bean is absent from the checkout I can verify, I stopped before the test gate.



## Verification failed

HEAD: e293738b67ffe997c14012a32cacf102d668bdc7
Working tree: clean

Still failing at acceptance check 1. The bean's cross-repo definition of done remains unmet in the sibling ../isaac-acp checkout I can inspect: features/comm/acp/slash_commands.feature still contains only the base status-command scenarios, git log for that feature still shows only 316464e, and the referenced config-command advertisement scenario from isaac-acp commit b6c9bbb is still absent. In addition, ../isaac-acp currently has a dirty worktree (src/isaac/comm/acp/cli.clj and spec/isaac/comm/acp/cli_proxy_reconnect_spec.clj modified), so even if new local changes were intended to address h34o, that checkout is not in a verifier-trustworthy state.
