---
# isaac-t1om
title: Retire :role directory-token alias of :cwd
status: in-progress
type: task
priority: normal
tags:
    - agent
    - foundation
    - config
created_at: 2026-09-15T19:50:19Z
updated_at: 2026-09-15T19:58:59Z
---

Retire `:role` as a directory-ACL token. It is a dwjy-era alias of `:cwd` (session workdir), not a crew key and not `~/agents/isaac/<role>`. ukg4 said no back-compat aliases; this leftover stayed.

## Decisions

- Decision (2026-09-15, Micah): delete the `:role` alias. Use `:cwd`. Clean cutover — `:role` is invalid, not silently ignored.
- Tags `:role/worker` and the crew `:cwd` string field are unrelated and stay.

## Scope

**isaac-foundation**

- `:cwd-or-path?` accepts only `:cwd`, `:quarters`, or a string. Message drops `:role`.
- Update `spec/isaac/config/validation_spec.clj`.

**isaac-agent** (pin foundation after the lexicon lands)

- `isaac.tool.names/expand-directory-token`: drop the `:role`/`"role"` branch.
- Manifest descriptions: tokens are `:cwd`, `:quarters`, or absolute paths.
- Broad-directory warnings: say `:cwd`, not `:role`.
- Live configs that still list `:role` must switch to `:cwd` before deploy (zane-isaac). Not this bean's checkout.

## Scenarios

`features/tool/directories.feature` @ 8e7cab5

- `:34` Global cwd grant — **keep**
- `:64` `:role` is not a directory grant — **new @wip** (allow `[:role]` does not open session cwd)

`features/config/cli.feature` @ 8e7cab5

- `:281` dwjy state-root warning — **edit @wip**: hint is `:cwd`, not `:role`
- `:299` validate rejects the retired `:role` directory token — **new @wip**

New steps invented: none.

At landing: remove `@wip` from those three.

## Exceptions

Authorized (2026-09-15): add the directories.feature `:role` scenario; change the dwjy warning assertion from `:role` to `:cwd`; add the validate-reject scenario.

## Acceptance

```
cd isaac-foundation
bb spec spec/isaac/config/validation_spec.clj
bb ci

cd isaac-agent
ISAAC_GIT=1 bb features features/tool/directories.feature
ISAAC_GIT=1 bb features features/config/cli.feature:281 features/config/cli.feature:299
bb spec spec/isaac/config/checks_spec.clj spec/isaac/tool
bb ci
```

One-time: `git grep -n ':role' -- src/isaac/tool/names.clj src/isaac/config/checks.clj resources/isaac-manifest.edn` in isaac-agent has no directory-token hits.
