---
# isaac-hvw9
title: Busy Discord messages park on the turn queue instead of dropping
status: draft
type: feature
priority: high
tags:
    - discord
    - queue
created_at: 2026-09-05T17:56:02Z
updated_at: 2026-09-05T17:56:02Z
---

Busy Discord messages park on the isaac-ohsy turn queue instead of being dropped.

## Problem

`process-message!` always `api/dispatch!`s. After routing, the bridge `mark-in-flight!`s the conversation's session. A second MESSAGE_CREATE on that conversation gets `{:dispatched? false :reason :session-in-flight}`. Discord does not look at that map. The message is gone.

`features/session/concurrency.feature` says callers decide how to handle the refusal. Hail defers. CLI re-presents. Discord chose silence. That is the eaten half-message.

Do not inject into the in-flight turn. The model already has its batch. A follow-up is the next turn on the same conversation (channel → episode, isaac-gx2q).

## Decision (2026-09-05, Zane)

**A, Discord only.** When dispatch would refuse `:session-in-flight` and the charge origin is Discord, park on the existing turn-request queue (isaac-ohsy) keyed by the session after routing. FIFO: one parked turn per message. No coalesce. No ack in this bean (typing already covers the live turn).

CLI stays refuse (interactive). Hail keeps its own busy-deferral — do not double-queue origin `:hail` / `:cron`. iMessage and ACP are out of this bean (same drop today; separate if we want them).

Not a Discord-only inbox in the gateway (B). That dies on restart and leaves the refuse path in the bridge.

## Desired behavior

- Channel C999, episodes crew, turn in flight. Second MESSAGE_CREATE parks (`turns list` shows it; `:turn.queue/held` logged). No reply yet.
- First turn ends → release token / queue tick wakes the parked charge → it runs as the next turn on the same episode; REST reply still goes to C999 (origin channel-id).
- Chronicle crew, same: park on session `discord-C999`, not a new session.
- Two follow-ups during one turn: both park, run in submit order after the live turn, no merge.
- Restart while a Discord message is held: the held record survives (ohsy durability) and runs on the next wake.
- CLI second prompt on an in-flight session still refuses `:session-in-flight` (`concurrency.feature` stays).
- Hail busy-session deferral unchanged.

Park reuses `park-held-charge!` / `persist-parked-user-message!` so the user line is on the transcript before wake; `:from-queue?` already skips a second append in `execute-llm-turn!`.

## In

**isaac-agent** first: `bridge/dispatch-charge!` currently `refuse-dispatch`s when `mark-in-flight!` fails. For Discord origin, park instead. May need a hold reason distinct from turnstile `:hold` so `turns list` is readable (`:session-in-flight` / busy). Wake path (`isaac.turn.worker`) already re-`dispatch!`s; if the session is still busy it must stay held, not drop.

**isaac-discord** scenarios prove the product: inbound MESSAGE_CREATE during a live turn parks and later replies. Discord `process-message!` should not grow its own queue.

## Out

- Coalescing two messages into one prompt.
- Reaction / "queued" ack.
- iMessage, ACP.
- Hail router cutover onto the queue (isaac-l3vb).
- Changing session single-writer (`max-in-flight` 1).
- 4zr3 persist lock, ggxc session pins.

## Relates

- isaac-ohsy (completed) — queue core this consumes.
- isaac-gx2q (completed) — Discord channel is the conversation thread; park/wake must keep origin channel-id.
- isaac-l3vb (draft) — hail address-spec cutover; do not block on it.

## Acceptance (sketch — scenarios not drafted)

Agent seam: Discord-origin dispatch while in-flight parks; CLI still refuses; two parks FIFO.

Discord: `features/comm/discord/episodes.feature` sibling — second MESSAGE_CREATE during a waiting echo turn does not reply until the first ends, then replies to C999.

Clean cutover: the drop is the bug; do not keep a "Discord ignores in-flight" scenario.

Do not implement from this draft. Promote to todo after scenarios exist (`/plan-with-features`).
