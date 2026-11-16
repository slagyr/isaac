---
# isaac-by8s
title: 'Subagents: parent stays available while children run (no sleep-polling; timer tool to check back)'
status: draft
type: feature
priority: normal
tags:
    - agent
created_at: 2026-11-16T14:44:40Z
updated_at: 2026-11-16T14:44:40Z
parent: isaac-4qxr
---

Repo: **isaac-agent** (delegation hint, new tool); possibly **isaac-cron** if the timer rides the scheduler.

Micah, 2026-11-16: "I would much rather that Zane is available for chats and that he sets timers for himself to check on the agents in the same session. This way I can continue to converse with Zane while he's got subagents going."

## Observed (zanebot, 2026-11-16, session 2026-09-09-2133-geus)

After spawning subagents Zane kept his turn open and polled: `exec__run sleep 45`, `sleep 90`, `session__read` on each child. That works, but the turn stays busy, so Micah can't converse until it ends.

## Change

- **Guidance.** The delegation hint (`turn_instructions.clj`, isaac-fnyp) says: after spawning, end your turn and stay available; results arrive on their own as a new message. Never sleep or poll for them.
- **Timer tool** for the fallback (a check-in, or a child that seems stuck): something like `timer__set {after, note}` that queues a turn on the *same* session after the delay, with `origin {:kind :timer}` and the note as input, the same way subagent results arrive. List/cancel optional. No timer tool exists today; isaac-cron only runs config-defined schedules.

## Open design questions

- Tool home: agent builtin vs. isaac-cron; name (`timer` vs. `schedule` namespace).
- Durability: does a pending timer survive a server restart (turn store / held turns)?
- Bounds: max delay, max pending per session.

## Acceptance

Draft — scenarios to come: a turn with subagents ends without sleeping (guidance present); a timer set for N seconds produces a `:timer` turn on the same session when the queue ticks past it.
