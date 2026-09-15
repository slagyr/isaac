---
# isaac-t1om
title: Retire :role directory-token alias of :cwd
status: completed
type: task
priority: normal
tags:
    - agent
    - foundation
    - config
created_at: 2026-09-15T19:50:19Z
updated_at: 2026-09-15T20:21:56Z
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

## Worker evidence (2026-09-15, scrapper@isaac-work-2)

Implemented the clean cutover in both repos.

- isaac-foundation `bean/isaac-t1om` @ `1c8e45b` (base `origin/main@ae4eda6`): `:cwd-or-path?` now accepts only `:cwd`, `:quarters`, or a string; the validation message and focused specs were updated. `bb spec spec/isaac/config/validation_spec.clj`: 22 examples, 0 failures, 25 assertions. Foundation `bb ci` reached the feature suite after 1026 specs passed, then hit two unrelated stale local gitlibs fixture failures referencing the removed `/Users/zane/agents/isaac/work-1/isaac-foundation-f21o/fixture-agent`; no t1om failures occurred.
- isaac-agent `bean/isaac-t1om` @ `2fa5037` (base `origin/main@8e7cab5`): pinned Foundation `1c8e45b`; removed `:role`/`"role"` expansion; changed broad-directory guidance and manifest descriptions to `:cwd`; updated unit coverage; removed `@wip` from the three authorized scenarios. `bb ci`: 1628 specs and 765 feature examples, 0 failures (one pre-existing pending). Focused directories: 8 examples, 0 failures, 8 assertions. Focused CLI scenarios: 2 examples, 0 failures, 8 assertions. One-time scoped grep reports no `:role` hits.

## Landed on main (2026-09-15)

main-sha: isaac-foundation a0a2b0f25bbdca9f391832ce0a11bc24137afd99
main-sha: isaac-agent 3f9fdc2814286b08c7005f6ad0f73b5cde7205f8
