---
# isaac-ggxc
title: Crew is the only source of session behavior; drop durable session pins
status: scrapped
type: task
priority: high
tags:
    - planning
created_at: 2026-09-05T05:13:23Z
updated_at: 2026-09-05T19:19:39Z
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



## Decisions (2026-09-05, Micah — planning session with the planner)

Verified in code first: session schema has mutable :model/:effort/:context-mode/:compaction (schema.clj:62-79); resolve-behavior* reads the session first for all four (context.clj:110-150); writers are `/model` (slash/builtin.clj:43), `/effort` (:114,:123), session__model (tool/session.clj:62), `sessions set` (any mutable field), and CREATE TIME — frequencies/behavioral-override projects :with-model/:with-effort/:with-context-mode into create-with-resolved-behavior! opts, which persists them (context.clj:200-232). Reads drop unknown keys (spec/isaac/session/schema_spec.clj "drops unknown keys on read"), so removing keys from the schema makes stale pins inert with no migration. The session :compaction map mixes policy keys with breaker state; 26 scenarios in 11 agent feature files seed compaction POLICY through session tables as a fixture.

1. **Slash commands**: remove `/model` and `/effort` entirely, including the no-arg display forms — `/status` already reports both. Why: a display form that cannot switch is a trap; clean cutover.
2. **Compaction fixtures**: the 26 session-table policy fixtures (bridge/cli-prompt, bridge/logging, comm/memory, session/{async_compaction, compaction_logging, compaction_memory_flush, compaction_requests, compaction_strategies, compaction_template, context_management, context_window_guard}) are rewritten to crew-level `compaction` config INSIDE this bean. Why: the worker touches those files anyway; a prep bean would just serialize the same edit. Session :compaction keeps ONLY breaker state (:consecutive-failures); policy keys leave the session schema.
3. **Breaker reset**: replace "switching model clears compaction-disabled" (compaction_logging.feature:290, used session__model) with "a crew model change resets compaction-disabled on the next turn". Why: preserves the operator recovery path (change the crew's model, next turn retries) without a session pin.
4. **`-M/--model` on `prompt`**: stays as the alias for `--with-model` (this-turn). Create-time persistence goes away: :with-* on a fresh session applies to the first turn only and is never written.
5. **Session :provider**: dropped with :model (only ever written alongside it; resolution derives provider from the model).

Clean cutover: no aliases, no deprecation shims; removed scenarios are deleted, not retained.



## Reasons for Scrapping (2026-09-05, Micah)

Ruled during the planning session, after the investigation above: keep the durable session overrides (:model/:effort/:context-mode/:compaction) and the surfaces that write them (`/model`, `/effort`, session__model, `sessions set`, create-time). Rationale: episodes already allow per-thread behavior, and a session pin is the same gesture for chronicle sessions; there is no reason to destroy it. The :with-* origin-scoped overrides (prompt, hail, hooks, Discord channels, cron, ACP --model) stay as they are. isaac-b3tl (session override wins after crew reload) remains in force. The decisions 1–5 recorded above are void. No feature files were changed.
