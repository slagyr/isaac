---
# isaac-ggxc
title: Crew is the only source of session behavior; drop durable session pins
status: draft
type: task
priority: high
tags:
    - planning
created_at: 2026-09-05T05:13:23Z
updated_at: 2026-09-05T05:16:42Z
parent: isaac-51xy
---

P2-2 slice: close the model-override ruling left open in isaac-51xy decision 35.

## Problem

A session record can pin `:model`, `:compaction`, `:effort`, `:context-mode` (and the surfaces that write them: `/model`, `session__model`, create-time `--model`, `sessions set`). That makes a session a pin bag, not a thread handle. Crew hot-reload already applies crew `:model` to the next turn (isaac-q5ee) unless a session pin wins (isaac-b3tl — this bean reverses that).

## Decision (2026-09-05, Zane)

- Kill **durable** session pins for model, compaction, effort, context-mode.
- Keep **this-turn** `:with-model` / `:with-effort` / `:with-context-mode` (hail, Discord channel, hooks, `prompt --with-model`). They never write the session.
- Cwd stays on the session (worksite/mission-scoped, decision 36).
- `/crew` is out of this bean (decision 35, crew-owned containers).
- `:conversation :chronicle` stays the named default-when-absent. This is not a session→chronicle rename.
- ACP identity stays fresh-mint (`acp-<uuid>` per toad connection). `--create always` is already unnecessary for episode crews (isaac-6yg0).

## Desired behavior

Resolve model / compaction / effort / context-mode from crew (+ model/provider/defaults) on every turn. Stale `:model` (etc.) on disk is ignored, not migrated. Surfaces that *wrote* those pins go away (clean cutover, no aliases). Surfaces that override *this interaction* stay.

## In

Likely **isaac-agent** (schema, `session.context/resolve-behavior*`, `create-with-resolved-behavior!`, slash `/model`, `session__model` tool, charge session-model forward). Downstream features in acp/discord/hail/hooks that *assert a session pin* need rewriting; `:with-model` scenarios stay.

Reverse isaac-b3tl: delete `features/crew/model_reload.feature` scenario "an explicit session-level model override still wins after a crew reload". Keep the crew-reload scenario.

## Out

- Terminology v2 rename (isaac-blqf, P2-1 transcript store).
- Dropping `:with-*` this-turn overrides.
- `/crew`, session cwd, hail `bound-session` spelling.
- Rewriting history / `filter-repo`.

## Reverses

- isaac-b3tl (completed): session `:model` override honored and survives crew reload.

## Acceptance (sketch — scenarios not drafted)

- Crew model change applies to next turn even if the session record still has a stale `:model`.
- `/model <alias>` and `session__model` are gone (unknown command / unknown tool), not no-ops that pretend to switch.
- `prompt --with-model X` still uses X for that turn and does not persist X on the session.
- Discord channel `:with-model` still applies per turn.

Clean cutover: no deprecated aliases, old override scenarios deleted not retained.


## Decision (2026-09-05, Zane) — ACP / conversation start

With `:conversation :episodes`, every conversation start is a new episode. `--create always` must not be required: `--crew marvin` (no session, no create flag) already mints a fresh thread, replays nothing, recall fills in. That is isaac-6yg0 / `features/comm/acp/episodes.feature` (`--crew on an episode crew attaches to a fresh thread and replays nothing`). Keep that contract. Do not introduce a stable reconnectable marvin ACP thread in this bean.
