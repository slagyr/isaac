---
# isaac-3kol
title: isaac init scaffolds Skipper as the first crew
status: completed
type: task
priority: normal
tags:
    - crew
    - foundation
    - cli
created_at: 2026-09-16T17:40:36Z
updated_at: 2026-09-16T18:31:07Z
---

`isaac init` scaffolds a crew named `main` with soul "You are Isaac, a helpful AI assistant." After dropping hardcoded `main` as a code identity, the first crew should be a character: **Skipper**.

## Decisions

- Decision (2026-09-16, Micah): init's default crew id is `skipper`. File `config/crew/skipper.md`. Soul: `You are Skipper. Keep the ship on course.` Heartbeat cron `:crew skipper`. `:defaults :crew skipper`.
- Existing installs are untouched. The refuse-when-config-exists scenario may keep a dummy `:crew :main` — that is not the scaffold.
- Independent of isaac-bfwn (required `:defaults :crew`). This only changes the *value* init writes.

## Scope (isaac-foundation)

- `isaac.cli.registry` `scaffold!` / `created-files`
- `spec/isaac/cli_spec.clj` init assertions

## Scenarios

`features/cli/init.feature` @ a1df8bc

- `:18` isaac init output lists created files — **edit @wip**: `config/crew/skipper.md`
- `:44` isaac init scaffolds each file with the expected content — **edit @wip**: `defaults.crew skipper`, skipper soul, heartbeat `crew: "skipper"`
- `:79` refuses when a config already exists — **keep**

New steps invented: none.

At landing: remove `@wip` from `:18` and `:44`.

## Exceptions

Authorized (2026-09-16): rewrite init scaffold assertions from `main` / "You are Isaac, a helpful AI assistant." to `skipper` / "You are Skipper. Keep the ship on course." Feature description file list too.

## Acceptance

```
cd isaac-foundation
ISAAC_GIT=1 bb features features/cli/init.feature
bb spec spec/isaac/cli_spec.clj
bb ci
```

## Implementation (2026-09-16, scrapper@isaac-work-3)

Root cause: `isaac.cli.registry/created-files` and `scaffold!` still hardcoded `main` and Isaac's generic soul.

Implemented branch `bean/isaac-3kol` @ `b10519c` (base `origin/main@a1df8bc`):

- init writes `:defaults {:crew :skipper ...}`;
- creates `config/crew/skipper.md` with `You are Skipper. Keep the ship on course.`;
- assigns heartbeat cron to `skipper`;
- reports the Skipper crew file in created output;
- updates CLI specs and removes `@wip` from the two owned feature scenarios.

Acceptance evidence:

- `ISAAC_GIT=1 bb features features/cli/init.feature`: 3 examples, 0 failures, 14 assertions.
- Focused changed CLI specs (`:205`, `:227`, `:261`) pass.
- Full `bb spec spec/isaac/cli_spec.clj` has one baseline failure at line 248 caused by berth registration logs in captured stderr; reproduced unchanged on clean `origin/main@a1df8bc`.
- `bb ci` reaches features and has two baseline `modules pins` failures because the cached fixture-agent remote points at missing `/Users/zane/agents/isaac/verify/isaac-foundation/fixture-agent`; unrelated to this bean.



## Landed on main (2026-09-16)

main-sha: isaac-foundation d0b5ff547f1c98d68da26f3ef1503ed1acb32b2b
