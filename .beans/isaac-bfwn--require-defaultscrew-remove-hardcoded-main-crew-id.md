---
# isaac-bfwn
title: Require defaults.crew; remove hardcoded main crew identity
status: todo
type: task
priority: high
tags:
    - agent
    - config
    - crew
created_at: 2026-09-16T15:49:57Z
updated_at: 2026-09-16T15:49:57Z
---

`:defaults :crew` is the default crew. Stop using a hardcoded `"main"` identity in production code.

## Problem

`isaac init` scaffolds `crew/main.md` and `:defaults :crew :main` — that name is fine. What is not: a second defaults system in code. Unlabeled sessions, session directories, quarters, charge, slash, prompt-cli, and `isaac crew list` (which **injects an empty `main`** if it is missing) all fall back to `"main"`. Charge also **skips the unknown-crew check** when the id is `"main"`, so a missing `main` crew is quieter than a missing `yopp`.

The schema already has `:validations [:crew-exists?]` on `:defaults :crew` and `:default "main"` — that schema default is the other implicit identity. Remove the default; require the field.

## Decisions

- Decision (2026-09-16, Micah): require `:defaults :crew` (must exist in `:crew`). Remove all production `"main"` crew fallbacks. Init may still scaffold a crew named `main` as the *value* of `:defaults :crew`. Tests may keep `"main"` as a fixture name.
- Clean cutover: a config with no `:defaults :crew` fails `config validate`. Do not invent a crew at runtime.

## Scope (isaac-agent)

- Manifest `:defaults :crew`: drop `:default "main"`; add `:present?`; keep `:crew-exists?`.
- Charge: last resort is `:defaults :crew` only; delete `(= crew-id "main")` from the unknown-crew exemption.
- Session open/store/paths, fs_bounds, prompt-cli, slash, tool session/memory: use session crew or `:defaults :crew`, never `"main"`.
- `isaac crew list`: do not `(assoc "main" {})`.
- Feature helpers (`session_steps` `open-session!`) stamp from defaults, not `"main"`.

Not this bean: isaac-episodes (follow-up, blocked_by this id). `isaac init` scaffolding of `crew/main.md` stays (foundation).

## Scenarios

`features/config/cli.feature` @ 3e6bf39

- `:189` validate requires defaults.crew — **new @wip**

`features/crew/cli.feature` @ 3e6bf39

- `:47` crew list with no configured crew members does not invent main — **edit @wip** (was “shows the default”)

`features/bridge/unknown_crew.feature` @ 3e6bf39

- `:59` a session with no crew uses defaults.crew — **new @wip**

New steps invented: none.

At landing: remove `@wip` from those three.

## Exceptions

Authorized (2026-09-16): add the validate-requires-defaults.crew scenario; retitle/rewrite the empty crew-list scenario so it does not expect `main`; add the unlabeled-session scenario.

## Acceptance

```
cd isaac-agent
ISAAC_GIT=1 bb features features/config/cli.feature:189
ISAAC_GIT=1 bb features features/crew/cli.feature:47
ISAAC_GIT=1 bb features features/bridge/unknown_crew.feature:59
bb spec spec/isaac/charge_spec.clj spec/isaac/config/schema_spec.clj spec/isaac/crew spec/isaac/session
bb ci
```

One-time:

- `git grep -n '"main"' -- src` in isaac-agent has no crew-identity fallback (session-key strings and fixture-only comments do not count). Charge `unknown?` does not special-case `"main"`.
- Manifest `:defaults :crew` has no `:default "main"`.
