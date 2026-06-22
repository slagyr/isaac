---
# isaac-8xb7
title: 'Scheduled reminders: one-time (and recurring) messages delivered to a comm'
status: draft
type: feature
created_at: 2026-06-22T17:39:12Z
updated_at: 2026-06-22T17:39:12Z
---

Let a user schedule a message to be delivered to a comm (iMessage, Discord, …) at a future time — most importantly a ONE-TIME reminder ("text me Thursday 9am to run the gpuswitch step"), and optionally recurring. Surfaced 2026-06-22 from the zanebot GPU-freeze mitigation: there was no way to have isaac remind Micah later, so he fell back to a calendar event. Likely lands as part of the cron module.

## Why it doesn't exist yet (building blocks vs gaps)
Already present:
- Foundation scheduler (`isaac.scheduler.runtime`) already supports one-shot triggers: `:at <instant>` and `:delay <ms>` (`:remaining-fires 1`) alongside `:interval`/`:cron`. So one-time firing exists at the engine level.
- Outbound delivery exists: comm `send!` (imessage/discord) + the persistent delivery queue/worker (`comm/delivery/pending/*.edn`).
- Cron fires a prompt to a crew on schedule.

Gaps:
- Cron CONFIG only exposes recurring `:expr` (5-field cron) — no user-facing one-time `:at`.
- Cron turns dispatch with the NULL comm (`null-comm/channel`); guidance says "the user may not see your reply." So a scheduled job's output is never delivered to a real comm.
- No crew-callable tool to send an outbound message / schedule a reminder.

Net: nothing wires "schedule (one-time or recurring) -> deliver a message to a named comm + recipient."

## Proposed direction (as part of cron)
- Expose one-time scheduling in config: `:at <ISO-8601/instant>` (and maybe `:in <duration>`) mapping to the scheduler's existing `:at`/`:delay` triggers, alongside today's `:expr`.
- Add a delivery target: a cron/reminder entry names a comm + recipient (reuse the delivery-record shape comm/target/content) so the fired output is enqueued to that comm instead of null-comm.
- One-time entries must retire after firing (not re-fire on restart) — via state file or auto-removal.

## Open design questions (refine before promoting to todo)
1. Plain message vs LLM turn? (a) fixed-text reminder — no LLM, cheap, predictable ("at T send text X to comm C"); (b) scheduled prompt -> crew turn -> deliver the reply ("text me a summary of …"). Support one or both? The fixed-text path is the simplest MVP.
2. Home: extend the cron module, or a dedicated lightweight "reminders" table/CLI? Cron is recurring-job-shaped; one-time reminders may deserve their own surface. (Micah leaned toward cron.)
3. Agent-native UX: the most natural path is a `schedule_reminder` TOOL the crew can call, so you just iMessage isaac "remind me Thursday to run gpuswitch" and it schedules + later delivers. Worth pairing with the config/CLI path.
4. One-time lifecycle: how a fired entry is marked done / removed so it never repeats; how it behaves if the host was down at fire time (cron already has missed-schedule handling — fire-late vs skip?).
5. Delivery routing: resolve comm + recipient (which imessage handle / discord channel); reuse delivery queue so it's retried/persistent.
6. Time zone: honor install tz for `:at`; clear semantics for past-due fires.

## Acceptance (sketch — pending design lock)
- A user can schedule a one-time reminder delivered to their iMessage at a specific local time; it fires once, delivers, and does NOT repeat.
- Survives a server restart (persisted; doesn't double-fire).
- Recurring variant (existing cron) can also deliver to a comm, not just null-comm.

## Notes
- Interim workaround for the gpuswitch reminder: a one-shot self-deleting launchd job calling `imsg send`, or (Micah's choice) a calendar event.
- Related: cron module (`isaac.cron`), scheduler `:at`/`:delay` triggers, delivery queue.
