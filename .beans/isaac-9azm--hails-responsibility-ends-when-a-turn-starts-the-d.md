---
# isaac-9azm
title: Hail's responsibility ends when a turn starts; the drive must not know about hail
status: in-progress
type: bug
priority: high
created_at: 2026-09-21T16:39:07Z
updated_at: 2026-09-21T16:41:48Z
---

Hail is a mailman. Its job is to get the message into a turn. Once a turn
starts, the letter is in the mailbox and hail is done. What happens inside the
turn — provider weather, suspension, cancellation, cycle limits, errors — is
the drive's business, and **the drive should know nothing about hail**.

Today hail holds the turn for its entire duration and makes six decisions at
turn end. Four of them are turn orchestration wearing a delivery costume.

## Decision (Micah, 2026-09-21)

**Hail's responsibility ends when a turn starts.** A delivery either started a
turn or it didn't. That is the only distinction hail draws.

## The evidence that this is clean

`finish-delivered!` (delivery_worker.clj:344) writes the delivery record — not
the turn's reply. `:reply-to` is only carried into the origin map for the turn
itself to use; hail never acts on it. The single `comm/on-bulletin` (:417) is a
continuations-exhausted failure notice.

**Hail never reads the turn's output.** It reads the turn's *disposition*, and
only to decide retry / defer / continue — the three things that aren't delivery.

## The six branches and where they go

The `cond` at `delivery_worker.clj:519`:

| # | branch | today | belongs to |
| --- | --- | --- | --- |
| 1 | `:unavailable?` | `defer-delivery!` + clears the session's turn marker | **drive** — delete |
| 2 | `suspended-response?` | logs only; delivery's queue file already gone | **drive** — delete |
| 3 | `cancelled-response?` | `finish-cancelled!` | drive; hail keeps a receipt at most |
| 4 | `:error` | `reschedule!` — attempts++, backoff, dead-letter at 5 | **splits — see below** |
| 5 | `:cycle-limit` | `wrap-up-delivery!` — continuation budget | **drive/band** — delete |
| 6 | `:else` | `finish-delivered!` | **hail** — but moves to bind time |

### Branch 4 is two different things

```clojure
;; delivery_worker.clj:516
(if (charge/unresolved? charge)
  {:error (:charge/reason charge)}   ; NO TURN EVER STARTED — hail's
  (turn/run-turn! charge))           ; turn ran and failed — the drive's
```

An unresolved charge is a **failed delivery**: retry, backoff and dead-letter
belong here and only here. A turn that started and then errored is not a
delivery failure. One branch currently handles both.

### Branch 6 moves earlier

`finish-delivered!` runs at turn end, which is the *sole reason* hail holds a
`future` for the turn's duration and deletes the queue file at claim
(`:625-627`). If "delivered" means "a turn started", the receipt is written at
bind, the record is never in limbo, and hail watches nothing.

## What this deletes in isaac-agent

Once hail stops owning turns, the drive stops knowing about hail:

- `resume.clj:224` — the `(= :hail source)` special case in `resume-marker!`
- `resume.clj:124` — `requeue-hail!`
- `resume.clj:60` — `marker->delivery`
- `bridge/core.clj:190` — the delivery payload embedded in the turn marker
  (`:hail-delivery` on the charge, `delivery_worker.clj:512`)
- `resume.clj:158` — `archive-cancelled-hail!` writing `hail/cancelled/`
- hail's `resume-grace?` / `:resume/requeued-at` handshake
  (`delivery_worker.clj:643-649`)

The drive already owns the weather half and it is **already written**:
`stamp-weather!` parks the turn with exponential backoff (30s doubling to a
30m cap) and `sweep-weather!` resumes it. `sweep-weather!` has **no production
caller** — it is reachable only from `spec/isaac/session/session_steps.clj`.
Wiring it is part of this work.

## Symptoms this subsumes

- **isaac-3wiu** — after a restart, recovery re-bound a work hail to
  `2026-06-29-1749-iaqu`, a three-month-old ad-hoc session, because
  `alternate-session` (:285) and the `runnable-delivery` fallback (:243) pick
  candidates from `delivery-crew-sessions` (crew-wide) with no reference to the
  band. Under this bean there is no re-bind to get wrong: the queue record never
  goes into limbo, so nothing is reconstructed. Keep isaac-3wiu as the narrow
  patch only if this work is deferred.
- **Branch 1 clears a marker the bridge deliberately keeps.**
  `suspend/release-turn-marker!` (agent :57) has an explicit `weather-parked?`
  branch returning `nil` to preserve it; hail reaches past
  `bridge/clear-turn-marker!` to `store/clear-turn-marker!` and deletes it
  anyway (landed today in 7e94025). It is correct *given* the current ownership
  split — without it the next tick's stale-marker guard reads the kept marker as
  a claim-crash stray and deletes the parked delivery — but two components now
  disagree about who owns the park. It works only because `sweep-weather!` is
  not wired.
- **Weather retry never backs off for hail deliveries.** That same clear
  discards `:suspend-count`, which is what `stamp-weather!` uses to escalate.
  Hail's `defer-delivery!` (:382) reuses the provider's raw `retry-after-ms` and
  never escalates, so a hail-driven session retries a downed provider at a flat
  interval forever. Latent today; live the moment `sweep-weather!` is wired.

## Open decision

Today a turn that dies on a transient error is retried up to five times with
backoff (`reschedule!` :431, `delays-ms`). Under this bean it would not be — a
failed turn stays failed and shows in the session. **If that retry is worth
keeping it must move to the drive as turn-level retry, not stay in hail as
delivery retry.** Decide before implementing.

## Acceptance

- hail writes the delivery receipt when a turn starts, not when it ends; the
  queue record is never deleted-and-unwritten
- hail's turn-end handling distinguishes only "no turn started" (retry /
  dead-letter) from "a turn started" (done)
- grep for `hail` in isaac-agent `src/` is clean — no `:hail` source case, no
  `marker->delivery`, no embedded delivery payload in the turn marker
- `sweep-weather!` is wired into a production path and provider weather on a
  hail-driven session backs off exponentially across restarts
- a restart with an in-flight hail turn resumes that turn in its own session;
  no session outside the hail's band is ever a candidate
- "hails never die" still holds: a provider outage parks and self-delivers on
  recovery, and nothing counts against the dead-letter budget

---

Dispatched: hail 7f6bf962 2026-09-21T16:41:10Z (band isaac-work, routed
:candidates 3, bound isaac-work-1 at 16:41:12Z)

## Conflict — cannot be implemented as written (2026-09-21, scrapper@isaac-work-1)

Investigated both repos from clean bases: `isaac-hail` `origin/main` `7e94025`,
`isaac-agent` `origin/main` `a0a4180`. No production code written — three
blockers, each a planner/human call, not a worker call.

### 1. The bean deletes behaviour that ~12 baselined scenarios assert

The bean carries no `## Exceptions` and no `feature-baseline:`. A worker may
only strip `@wip` from a `.feature`. Every branch in the bean's table is
currently pinned by green scenarios that must be re-cut or deleted **by the
planner, on module main**, before any implementation can be green:

| branch | scenarios pinning today's behaviour |
| --- | --- |
| 1 `:unavailable?` defer | `isaac-hail/features/delivery.feature:505, :537, :568, :603, :640` (isaac-3tvq / 6zk5 / 5a4n — landed today in `7e94025`) |
| 2 `suspended-response?` | `delivery.feature:665` (isaac-2xj5) |
| 3 cancelled | `delivery.feature:847` |
| 4 turn-error retry | `delivery.feature:172, :198, :300, :394, :418, :443` (isaac-k4mf / jnkp / cehc) |
| 5 `:cycle-limit` continuations | `delivery.feature:701, :741, :916` (isaac-ntt6 / tic5) |
| 6 receipt at bind | `delivery.feature:27, :330, :360, :816` assert the delivered-at-turn-end ordering |
| agent `requeue-hail!` | `isaac-agent/features/session/resume_repair.feature:49` "a legacy hail marker is requeued and removed from its original path" |

Branch 1's five scenarios landed **today** as isaac-3tvq/6zk5/5a4n. This bean
reverses that work. That is a planner-level contract decision, not a worker
edit.

### 2. The bean's own Open decision is unresolved

"Today a turn that dies on a transient error is retried up to five times …
**Decide before implementing.**" Acceptance 2 implies hail drops it; no
acceptance item adds a drive-side replacement. Ruling needed:
**(a)** no retry at all — a failed turn stays failed and shows in the session
(cheapest, satisfies every acceptance line as written); or
**(b)** turn-level retry moves into the drive — new isaac-agent work with its
own scenarios, and `delivery.feature:172/:198/:394/:418/:443` move to the agent.

### 3. Branch 5 hides a second decision of the same class

The table says `:cycle-limit` "belongs to **drive/band** — delete", but hail's
`continue-delivery!` / `wrap-up-delivery!` (`delivery_worker.clj:395-429`) is
the **only** continuation implementation in the tree. In isaac-agent,
`grep -rn "cycle-limit|continuation" src/` finds reporting only
(`drive/turn.clj:484, :493-507`) — nothing re-drives a budget-exhausted turn.
Deleting branch 5 with no replacement ends continuations outright, and the crew
work protocol depends on them ("Running out of budget is a wrap-up … the
delivery worker resumes on a fresh turn" — `work-bean-gate`). Ruling needed:
**(a)** hail keeps continuations (contradicts the table); **(b)** a drive/band
continuation budget is built first, as its own bean blocking this one;
**(c)** continuations end, and the bands that rely on them are re-cut.

### Scope note (for whoever re-plans it)

Even with 2 and 3 settled this is two repos and three separable pieces, each
with its own acceptance:

1. **drive-side continuation budget** (if ruling 3b) — isaac-agent, blocking.
2. **wire `sweep-weather!` + decouple the drive from hail** — isaac-agent:
   `resume.clj` loses `marker->delivery` / `requeue-hail!` / the `(= :hail
   source)` case / `archive-cancelled-hail!` (and `crash-orphan?` /
   `resume-attempts` go dead with them); `bridge/core.clj:189` drops
   `:delivery-id`; `:hail` joins `#{:comm :cron :cli}` on the
   `enqueue-resume-turn!` path, which is what acceptance 5 ("resumes that turn
   in its own session") actually means. `sweep-weather!` needs a production
   scheduler task — the agent has exactly two today
   (`comm/delivery/worker.clj:92`, `turn/worker.clj:118`) — and wiring it needs
   a guard against double-driving, because boot resume already enqueues a
   resume turn for a due suspended marker (`resume.clj:200-214`).
3. **hail: receipt at bind + branch collapse** — only after 2, since the
   `store/clear-turn-marker!` in the weather branch
   (`delivery_worker.clj:555-560`) exists precisely because `sweep-weather!` is
   unwired; removing one without the other loses the park or double-drives it.

Bean left `in-progress`, nothing implemented, no branch pushed. Worktrees
`../isaac-hail-9azm` and `../isaac-agent-9azm` removed.
