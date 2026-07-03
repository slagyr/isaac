---
# isaac-8lhv
title: 'hail-send tool: reject an explicit session that names no existing session (fast feedback, no dead-letter)'
status: draft
type: bug
priority: high
created_at: 2026-07-03T20:56:45Z
updated_at: 2026-07-03T21:01:36Z
---

## Problem (evidence, 2026-07-03)

Crews sometimes dispatch a hail with an explicit session value that names no existing session — most often the BAND NAME itself (session ["isaac-work"] appears 11x in zanebot records; a session named "isaac-work" never exists — the band selects isaac-work-1/2 by tags). These silently persist to pending and then dead-letter as undeliverable. The crew that made the mistake gets NO feedback (the send "succeeded"), so it never self-corrects, and the return is simply lost (contributed to isaac-wtg8 appearing stuck).

## Design (send-time validation in the hail-send TOOL — reject, don't coerce)

Rejected the earlier "router coerces session==band-name -> band routing" idea as sloppy: it makes the router guess intent from a string collision, is fragile, and hides the error. Correct fix: fail fast at the tool boundary so the model gets in-turn feedback and self-corrects on its next tool call.

In `isaac.tool.hail` (hail-send; it already has session-store access), when the tool call carries an EXPLICIT top-level session frequency (not band-driven selection) whose value names no existing session:

- Do NOT persist the hail. Return a tool ERROR result.
- Message is actionable, e.g.: `no session "isaac-work" exists. If you meant to route by band, pass band: "isaac-work". For an exact session use a real id (e.g. "isaac-work-1", from hail_get on the thread).`
- Special-case the common mistake: if the value equals a known BAND NAME, say so explicitly ("that is a band name, not a session").

Scope notes / do-not-break:
- Band-driven selection (session-tags/crew) and create:if-missing bands must be UNAFFECTED — this validates only an explicit session id supplied in the tool call.
- A hail-send with a genuine, existing explicit session id still sends (regression guard).

## Acceptance scenarios (to be committed @wip after review)

isaac-hail features/crew-tool.feature:
- A crew hail-send whose explicit session equals a configured band name returns a tool error naming the mistake; NO hail is persisted; the turn can retry.
- A crew hail-send with an explicit session that names no session at all returns the actionable error; no hail persisted.
- A crew hail-send with a real existing explicit session still dispatches normally (regression).

## Scope

isaac-hail (tool/hail.clj send-time validation + tool error result; features/crew-tool.feature). Prose counterpart: isaac-ukof.
