---
# isaac-by8s
title: 'Subagents: parent stays available while children run (no sleep-polling; timer tool to check back)'
status: completed
type: feature
priority: normal
tags:
    - agent
created_at: 2026-11-16T14:44:40Z
updated_at: 2026-11-16T17:30:16Z
parent: isaac-4qxr
---

Repo: **isaac-agent** (delegation hint, new tool); possibly **isaac-cron** if the timer rides the scheduler.

Micah, 2026-11-16: "I would much rather that Zane is available for chats and that he sets timers for himself to check on the agents in the same session. This way I can continue to converse with Zane while he's got subagents going."

## Observed (zanebot, 2026-11-16, session 2026-09-09-2133-geus)

After spawning subagents Zane kept his turn open and polled: `exec__run sleep 45`, `sleep 90`, `session__read` on each child. That works, but the turn stays busy, so Micah can't converse until it ends.

## Change

- **Guidance.** The delegation hint (`turn_instructions.clj`, isaac-fnyp) says: after spawning, end your turn and stay available; results arrive on their own as a new message. Never sleep or poll for them.
- **Timer tool** for the fallback (a check-in, or a child that seems stuck): something like `timer__set {after, note}` that queues a turn on the *same* session after the delay, with `origin {:kind :timer}` and the note as input, the same way subagent results arrive. List/cancel optional. No timer tool exists today; isaac-cron only runs config-defined schedules.

## Decisions (2026-11-16, Micah)

Modeled on Claude Code's own tools (ScheduleWakeup, foreground-sleep block):

- **Tool home:** isaac-agent builtins, namespace `timer`: `timer__set {after, note}`, `timer__list`, `timer__cancel {handle}`; granted with `:timer/*`. A timer is a delayed turn submission (same session, `origin {:kind :timer}`, note as input), not isaac-cron.
- **Durable:** a timer is a turn record with a due time, so it survives a restart. The queue skips it until due.
- **Bounds:** `after` 60..3600 seconds (tool error outside); `:max-timers` per session, default 10 (crew key, schema `:description`), counting pending timers.
- **No sleep-polling:** `exec__run` refuses a command that starts with `sleep N`, N ≥ 5, alone or followed by `;` / `&&`, with an error pointing at `timer__set`. Short sleeps inside loops still run.
- **Guidance:** the delegation hint (isaac-fnyp) adds: after spawning, end your turn and stay available; results arrive on their own; never sleep or poll; use `timer__set` to check back. Unit-spec it.
- Handbook: timers section in `resources/isaac/agent/handbook.md`.

## Acceptance

Scenarios in `isaac-agent/features/tool/timers.feature` (feature-level `@wip`; remove it when all pass):

- `bb features features/tool/timers.feature:19` — a timer comes back to the same session when due, not before (`origin.kind timer`)
- `bb features features/tool/timers.feature:43` — `after` outside 60..3600 is a tool error naming the range
- `bb features features/tool/timers.feature:50` — `timer__list` shows it; `timer__cancel` stops it and empties the list
- `bb features features/tool/timers.feature:69` — `:max-timers 1` refuses a second timer (`timer limit reached (1 pending)`)
- `bb features features/tool/timers.feature:82` — `exec__run "sleep 45"` is refused, pointing at `timer__set`

Run the whole file: `bb features features/tool/timers.feature`.

feature-baseline: isaac-agent d4038f1d242b33e489a800f46879eaf4602e8b17
feature-blob: isaac-agent features/tool/timers.feature 8003ba8f44c133e0c09de011ce5952ecae1ac29c

## Held (awaiting planner, 2026-11-16)

Implementation on isaac-agent bean/isaac-by8s (428e684) passes all five timers scenarios (bb features features/tool/timers.feature: 5/5) and bean-gate verify PASS. Full bb ci is RED (2012 specs green; 973 features, 8 failures) because existing baselined features conflict with the new contract: features/tool/built_in.feature:243 expects exec__run sleep 10 to time out, while this bean explicitly requires refusing sleep >=5; features/bridge/suspend.feature:59 expects exec__run sleep 30 to keep running and yield an unclean shutdown marker, while the new contract requires refusing it. Five features/tool/permissions.feature scenarios assert an exact old built-in tool set omitting timer__set/list/cancel; adding the required built-ins breaks those assertions. One bridge/cancel.feature failure was intermittent (absent on the second full run). Tried adjusting non-contract code to avoid failures but cannot satisfy both approved contracts honestly. Planner must revise/re-baseline the conflicting legacy feature scenarios before bb ci can pass; no feature contract edited. Resume at isaac-agent-isaac-by8s on bean/isaac-by8s, run bb ci then gate and land.

## Planner note (2026-11-16, after the worker held on contract conflicts)

- **Sleep fixtures fixed on main** (isaac-agent, "long-running exec fixtures loop short sleeps"): `cancel.feature` ×2, `suspend.feature`, `built_in.feature` (timeout) used `sleep 30` / `sleep 10` as a long-running command; they now run `while :; do sleep 1; done`, which the new rule allows. Green on main. `timeout.feature:62` (`sleep 1; echo finished`) is under the 5 s threshold and stays.
- **Tool lists don't change:** timer tools are opt-in like the subagent tools — NOT part of `:all`; a crew gets them only by naming `:timer/*` (or a member). Exclude them where `tools_config.clj` already excludes `subagent__*` from `:all`. So `permissions.feature`'s exact `:all` lists stay as they are.
- No change to `timers.feature`; the baseline stands.

## Landed on main (2026-11-16)

main-sha: isaac-agent 70a30c9f7bf835b8861136283f8880fc86d84163

Validation: `bb ci` (2018 specs, 973 features; 0 failures, 1 pre-existing pending), `bb bean-gate verify isaac-by8s --dir isaac-agent=../isaac-agent-by8s-land` PASS on squash commit.
