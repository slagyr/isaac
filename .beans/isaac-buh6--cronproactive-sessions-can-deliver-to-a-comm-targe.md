---
# isaac-buh6
title: Cron/proactive sessions can deliver to a comm target (retire null-comm + osascript)
status: todo
type: feature
priority: high
created_at: 2026-06-21T15:48:10Z
updated_at: 2026-06-21T15:48:10Z
blocked_by:
    - isaac-ve2a
---

A cron job runs its prompt and DISCARDS the output — `isaac.cron.service` wires
every cron session to `null-comm/channel` with `:origin {:kind :cron …}`. The
cron schema has only `:crew`/`:expr`/`:prompt`; there is no way to address a
real comm. So a proactive session is NOT connected to any channel, and the only
way to get a message out is the agent shelling `osascript`→Messages.app (see
zanebot `config/cron/health-checkin.md`). That AppleScript path is flaky (120s
timeouts) and bypasses `imsg` entirely.

Goal: let a proactive (cron, and later hook) session be addressed to a comm +
recipient, so its origin IS that comm and the agent's normal response delivers
through it (`imsg send` for imessage) — no AppleScript, no special tool.

## Tasks
- [ ] Cron schema: add a delivery target (e.g. `:comm :imessage` + `:to "<handle>"`,
      shape TBD) on a cron entry; validate the comm exists.
- [ ] Cron service: when a target is set, run the session against THAT comm +
      recipient (not `null-comm`), so the response is delivered like a reply.
      Default (no target) keeps today's discard behavior.
- [ ] Rewrite zanebot `config/cron/health-checkin.md`: delete the `osascript`
      block; the prompt just produces the alert/summary text, Isaac delivers it.
- [ ] Consider generalizing the same addressing to hooks / any proactive session
      (origin framing — see ho1s). Out of scope to implement here; note the seam.

## Dependency
- **Blocked by ve2a** — the delivery worker must resolve the live comm instance
  first; otherwise a targeted cron just dead-letters `:permanent`.

## Acceptance
- A cron entry addressed to the imessage comm delivers its output to the
  configured recipient via `imsg send` (over the ssh wrapper), no osascript.
- `health-checkin.md` contains no `osascript`; the 9 AM health review sends
  through the comm.
- Untargeted crons (e.g. heartbeat) still run-and-discard as today.
