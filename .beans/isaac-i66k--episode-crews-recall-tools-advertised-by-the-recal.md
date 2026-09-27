---
# isaac-i66k
title: 'Episode crews: recall tools advertised by the recall block must actually be allowed'
status: todo
type: bug
priority: normal
created_at: 2026-08-29T04:43:16Z
updated_at: 2026-08-29T04:43:16Z
---

Repo: **isaac-agent** (tool allow cascade). Acceptance lives in
**isaac-episodes** because that suite registers the recall tools.
Found in the isaac-h5dk field check, 2026-08-28. Seen again on Yopp
2026-09-26: the crew is `:session-policy :episodes`, scenes are cut and
indexed, and the allow list never named `:recall/*`, so the model had no
`recall__search` or `recall__scene`.

## Decision (2026-09-27, Micah)

An episodes crew receives `recall__search` and `recall__scene` whether or
not its allow list names them. A crew that denies `:recall/*` does not
receive them. A chronicle crew receives them only when its own allow list
says so. The implicit grant must not undo a deny: adding `:recall/*` onto
the crew allow list would, because crew allow is applied after crew deny.

Yopp's allow list was given `:recall/*` by hand on 2026-09-27 so the crew
can call the tools before this lands. That line becomes redundant once the
grant is implicit, and it should stay.

## Acceptance

`features/recall/implicit_tools.feature` (@wip):

- an episodes crew whose allow list is only `fs/read` is offered
  `fs__read`, `recall__search`, and `recall__scene`
- an episodes crew with no tools section is offered only the two recall tools
- an episodes crew that allows `fs/read` and denies `:recall/*` is offered
  only `fs__read`
- a chronicle crew that allows `fs/read` is offered only `fs__read`

```
cd isaac-episodes && bb features features/recall/implicit_tools.feature
```

Remove `@wip` as each scenario passes. `bb ci` in isaac-agent and isaac-episodes.

feature-baseline: isaac-episodes a72577310bf7bc4ee3087d48101183b79ee3e819
feature-blob: isaac-episodes features/recall/implicit_tools.feature a9ad0b2c1670e9970dd4c8891fc34db56f1c7467 12,35,56,78
