---
# isaac-dwjy
title: 'Crew filesystem sandbox: scope crew directories to the role workspace'
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-07-04T14:35:53Z
updated_at: 2026-07-04T14:55:43Z
---

## Problem

Worker crews have far-too-broad filesystem write access. scrapper's `:directories` grant `/Users/zane/Projects` and all of `/Users/zane`, so a crew can write anywhere in the user's home — including `~/.isaac` (config, prompts, sessions) and unrelated projects.

## Evidence (2026-07-03)

- A crew deleted the deployment-owned `zane-toolbox` skill from `~/.isaac/prompts/skills/` (no logged rm; only scrapper had the reach).
- isaac-wtg8's work was done in `/Users/zane/Projects/isaac-discord` (the user's personal clone) instead of an isolated role-home sibling, because the correct sibling wasn't cloned AND scrapper could reach Projects/.

## Desired behavior

A crew's filesystem access is scoped to its role workspace (e.g. `~/agents/isaac/<role>/`), forcing the design's clone-the-module-sibling-on-demand behavior and keeping crews out of `~/.isaac`, unrelated projects, and personal files. Default crews to a narrow sandbox; broad access must be explicit and rare.

## Scope

isaac-agent (crew `:directories` resolution / tool exec path bounds) + the crew config convention. Possibly a validation warning when a crew is granted a home-wide or `~/.isaac`-inclusive directory.

## Proposed acceptance

- A crew scoped to its role dir cannot write outside it (exec/write/edit rejected or bounded).
- A crew needing a module repo clones it under its role home rather than reaching into a shared/personal clone.
- Config validate warns when a crew directory includes the isaac state dir or the whole home.

Priority: NORMAL (safety/isolation; not an outage, but caused real damage).
