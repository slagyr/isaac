---
# isaac-ugpq
title: 'Epic: turns suspend and resume on provider weather (wall/auth/stall) — origin-agnostic via the turn marker; hail stops retrying'
status: draft
type: epic
priority: high
tags:
    - durability
    - turn
    - hail
created_at: 2026-09-18T14:42:12Z
updated_at: 2026-09-18T14:42:12Z
---

## Why (Micah, 2026-09-18)

When a provider walls mid-turn (429 / auth / stall), the drive ENDS the turn with `{:unavailable? true :retry-after-ms N}`. Nothing in the turn layer resumes it. The hail delivery worker papers over this for hail-originated turns only: it defers the delivery and re-sends the original prompt later, starting a NEW turn that re-orients from the transcript. Costs seen this week: cron-fired turns that wall just end (and record success — isaac-7ngj); Discord-originated turns that wall end silently; every hail retry re-pays the full context (30 × 1.5 MB/h overnight); and hail carries retry policy that belongs to the turn.

A wall is, from the turn's point of view, the same event as a server restart: the work was interrupted, not finished. Isaac already handles the restart case origin-agnostically — the durable turn marker (isaac-7li9, `turn.edn` with the persisted charge) plus the boot resume scan re-drives in-flight turns (`resume/scan-complete :requeued N`). Provider weather should use the same machinery.

## Decision (2026-09-18, Micah)

**Turns suspend and resume themselves; hail stops retrying.**

1. On provider weather (`provider-wall/classify` → `:unavailable?` with `:reason :wall|:auth|:stall` and `:retry-after-ms`), the drive does not end the turn. It persists `:state :suspended :reason … :retry-at <iso> :suspended-at <iso>` into the turn marker (charge already there) and releases the thread. The session's in-flight slot is released; the session stays "parked" so the turn queue (isaac-ohsy) holds any new requests for it behind the suspended turn.
2. A **resume sweep** on the shared scheduler (same component family as the delivery worker) re-drives suspended turns whose `:retry-at` has passed, oldest first, honouring crew capacity. Re-driving = the same cycle loop continuing from the transcript, exactly as boot resume does today. If the provider walls again, back off (retry-after or exponential, capped) and re-suspend; suspensions are not attempts and never dead-letter. `:unavailable :reason :auth` re-suspends until re-login ([[zanebot-grok-subscription]]).
3. Boot resume already re-drives markers; it now also honours `:retry-at` for `:suspended` markers instead of re-driving immediately.
4. **Origin handling on suspend**: the origin is told once (`:turn/suspended` event; hail logs it on the delivery, cron records `:last-status :suspended`, comms MAY post a one-line "provider unavailable, will resume" — open below) and told again on completion through the existing completion path. Hail's delivery stays claimed/in-flight across the suspension and completes when the turn does.
5. **Hail removes `defer-delivery!` on `:unavailable?`** and the retry-after bookkeeping around it. Hails-never-die (isaac-5a4n) is preserved — by the turn layer now: a walled turn resumes on its own, attempts untouched. The dead-letter budget is for turns that END in error.
6. Cron: `:last-status :suspended` on suspend; final `:succeeded|:failed` on completion (supersedes the wall row of isaac-7ngj's scenario :99 — that scenario currently expects `:failed`; it becomes `:suspended` → `:succeeded` when this lands).

## Children
1. **isaac-nqeq** — drive suspends on weather; marker state; resume sweep; boot resume honours retry-at; `:turn/suspended` + `:turn/resumed` events; session parked semantics with the turn queue.
2. **isaac-q2v5** — delete defer-on-unavailable; delivery stays claimed through a suspension; `hail show` prints suspended state; scenarios in delivery.feature re-cut.
3. **isaac-a0q6** — `:suspended` status; final status on completion (re-cuts 7ngj :99).
4. **isaac-h5v8** — Comms (Discord/iMessage/ACP) — one-line suspend notice and the eventual reply; decide per comm (open).

Prerequisite/related: isaac-v64q (mid-stream 429 must classify as weather, or the drive never sees `:unavailable?` to suspend on). isaac-1umd / `:stateful true` reduces what a resumed cycle re-sends.

## Open
- Does a suspended turn count toward `:max-in-flight`? Proposed: no (slot released), but the session is parked so no second turn starts on it.
- Max suspension age before a turn is failed instead (a week?), so a dead provider config can't park a session forever. Proposed: configurable `:turn :max-suspended-ms`, default 24 h, then `:error :provider-unavailable` and the origin's error path.
- Comm notice wording/whether at all (Discord users vs. hail).

Status: DRAFT — design for review; children get scenarios after Micah signs off on the decisions above.
