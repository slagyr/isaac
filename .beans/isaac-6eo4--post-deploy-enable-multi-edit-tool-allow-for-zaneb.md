---
# isaac-6eo4
title: 'Post-deploy: enable multi_edit tool-allow for zanebot work crews'
status: todo
type: task
priority: normal
created_at: 2026-07-09T17:26:10Z
updated_at: 2026-07-09T17:26:10Z
---

## Goal

Post-deploy one-time ops rollout for the `multi_edit` builtin (isaac-k1po):
enable it for zanebot work crews once the agent build that registers the tool is
actually deployed.

## Why this is a separate bean

The crew `:tools :allow` entry only validates after the isaac-agent build that
*contains* the `multi_edit` builtin registration is deployed and pinned in the
module registry. On the live install, `isaac config validate` correctly rejects
`:multi_edit` while the running agent predates the builtin:

    error: crew.scrapper.tools.allow - must be a registered contribution
    to :isaac.agent/tools [bad value: multi_edit]

So the rollout is inherently post-merge/post-deploy and cannot be satisfied as
pre-merge acceptance on isaac-k1po. Split out here.

## Prerequisite

- isaac-k1po merged to `isaac-agent` main.
- `modules.edn` `:isaac.agent` pin bumped to the merged commit and reinstalled on
  zanebot (so `:isaac.agent/tools` registers `multi_edit`).

## Ops steps

1. Confirm the deployed agent registers the tool: `isaac config` /
   tool listing shows `multi_edit` as a registered `:isaac.agent/tools`
   contribution.
2. Add `:multi_edit` to the `:tools :allow` list of zanebot work crews
   (`scrapper`, `:tags #{:role/worker}`) in
   `~/.isaac/config/crew/scrapper.edn`, immediately after `:edit`.
3. Validate: `isaac config validate` from `~/.isaac` passes with no new errors.

## Acceptance

- [ ] `isaac config validate` passes with `:multi_edit` present in a work crew's
      `:tools :allow`.
- [ ] A work session can invoke `multi_edit` (smoke: one multi-entry call
      applies and reports per-entry summary).
