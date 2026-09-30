---
# isaac-7pdj
title: 'Config validation parity: the server refuses what the CLI refuses'
status: todo
type: feature
priority: high
tags:
    - security
    - http
created_at: 2026-09-20T00:25:07Z
updated_at: 2026-09-30T14:05:33Z
parent: isaac-gym1
---

Repo: **isaac-foundation**. Micah, 2026-09-19: "that inconsistency within value config is really wrong. Let's fix that."

## Why

The CLI and the server disagree about what a valid config is. On zanebot on 2026-09-19 the retired `:server :auth :token`, `:server :host` and `:server :burst` keys made `isaac sessions list` and `isaac auth status` fail outright with "invalid configuration", while the server booted on that same config, ignored those keys, bound to 127.0.0.1 instead of 0.0.0.0 and ran with no HTTP auth at all. The operator gets a hard stop from the CLI and silence from the thing that actually serves traffic.

A retired key that carries a real value is not a warning. It is an instruction the operator believes is in force.

## Change

- Boot refuses the same errors the CLI refuses. A config that fails validation stops the runner with the same report, before any listener binds.
- A retired key carrying a value is an error at both surfaces, naming the replacement key. A retired key is never silently dropped.
- `isaac modules upgrade` must not rewrite a config into an invalid one: it validates the result before writing, and refuses with the offending keys named. (Tonight's rewrite dropped the whole `:server` block.)

## Acceptance

Scenarios (worker writes, isaac-foundation `features/`): a config with a retired key carrying a value fails `isaac config validate` and fails the runner with the identical message; the same config with the key absent boots; `modules upgrade` on a config with retired keys refuses and leaves the file untouched; a valid config still boots unchanged.

Related: isaac-bsqm (hail delivery stall on the same host).

## Triage update (2026-09-30, planner, approved by Micah)

Much of this landed: isaac-7fge makes the server refuse to bind on config errors (isaac-http app.clj start! + cli.clj run), and foundation has a generic :retired? validation (validation_lexicon.clj). Remaining: (1) confirm the refusal covers every config error, not just http auth; (2) isaac modules upgrade still treats pre-existing retired-key errors as carried warnings (mutate-modules! → set-config with skip flags), so the original 'upgrade dropped the :server block' failure may still be reachable. Rescope to those two.
