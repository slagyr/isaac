---
# isaac-dv7p
title: config set/unset edit an <id>.md entity's frontmatter in place instead of creating a sibling .edn
status: in-progress
type: bug
priority: high
tags:
    - foundation
    - config
created_at: 2026-09-25T16:42:02Z
updated_at: 2026-09-25T16:42:47Z
---

Repo: **isaac-foundation** (src/isaac/config/mutate.clj, where the target file is chosen). Scenarios live in **isaac-agent** (features/config/cli.feature, in the "Set" file-placement section), because crew is the frontmatter entity agent's fixtures exercise.

## The problem (zanebot, 2026-09-25)

`isaac config set cron.tempest-vault-sync.with-model gpt-sol` printed `set … (cron/tempest-vault-sync.edn)` and exited 0, but nothing changed. The job is defined in `cron/tempest-vault-sync.md` (frontmatter + prompt body). The set created a new sibling `cron/tempest-vault-sync.edn`; at load the `.md` frontmatter won, so `config get` still returned the old `"gpt"`. The planner fixed it by hand (deleted the `.edn`, edited the frontmatter).

The cause: mutate.clj knows an entity's `.md` only as the home of its **companion body field** (`companion-spec`, mutate.clj:39: soul, prompt, template). For every other field it picks `<id>.edn` (or isaac.edn), even when the entity lives in `<id>.md` with frontmatter. This affects every frontmatter entity type: crew (`crew/<id>.md`), cron, hooks, and anything declared `:frontmatter? true`.

## Change

When the entity is defined by `<entity-dir>/<id>.md` with frontmatter, `config set` and `config unset` on any of its non-body fields edit that file's frontmatter in place:

- the body (everything after the closing `---`) is preserved byte for byte
- untouched frontmatter keys keep their order
- never create a sibling `<id>.edn`
- the confirmation and the `:config/set` log entry name `crew/<id>.md` as the file
- unsetting the last frontmatter key leaves a valid `.md` (either an empty frontmatter block or none; worker's choice)

Set-member paths (`…tags.<member>`) and the stdin-map form (`config set crew.<id> -`) follow the same rule: they write to the `.md` frontmatter when that's where the entity lives.

## Decisions

- Decision (2026-09-25, Micah): fix it as its own bean rather than folding it into isaac-i5on, which is mid-verify and only changes output.
- Decision (2026-09-25, planner): the file that already defines the entity is where writes go. That extends the existing rule "set writes to the existing entity file when one already defines the key" (cli.feature) to `.md` entities. The body/companion routing stays as it is.

## Scenarios (isaac-agent, @wip)

- features/config/cli.feature:627 — set edits the frontmatter of an entity that lives in `<id>.md` (file matches, no `.edn`, log names the `.md`, `config get` shows the new value)
- features/config/cli.feature:659 — unset removes a frontmatter field; the body and the other keys survive

Both were dry-run against current main: they fail at their first file assertion (the `.md` is unchanged), which is the bug.

## Acceptance

```
cd isaac-foundation && bb spec && bb ci
cd isaac-agent && bb features features/config/cli.feature:627 features/config/cli.feature:659
cd isaac-agent && bb features features/config && bb ci
```

Remove `@wip` from both scenarios. Land foundation, repin agent, land agent. isaac-i5on also touches foundation config CLI code (mutate_common.clj, not mutate.clj), so rebase onto whatever foundation main is when you start.
