---
# isaac-3kol
title: isaac init scaffolds Skipper as the first crew
status: in-progress
type: task
priority: normal
tags:
    - foundation
    - cli
    - crew
created_at: 2026-09-16T17:40:36Z
updated_at: 2026-09-16T17:53:29Z
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
