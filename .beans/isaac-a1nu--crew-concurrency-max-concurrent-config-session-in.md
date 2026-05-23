---
# isaac-a1nu
title: 'Crew concurrency: max-concurrent config + session in-flight tracking'
status: in-progress
type: feature
priority: normal
created_at: 2026-05-23T00:24:48Z
updated_at: 2026-05-23T04:35:29Z
---

## Motivation

Today every crew implicitly runs one turn at a time, and there's
no observable signal of "is this session currently in a turn?"
Hail's fan-out slice depends on both:

- Picking an idle session over a busy one when multiple match a
  subscription's tags.
- Capping the number of simultaneous sessions a crew can run, so
  a queue-group hail (or a cron + hail collision) doesn't spin up
  unbounded parallel work.

This bean adds two small primitives, both useful beyond Hail:
declarative concurrency caps in crew config, and observable
in-flight state on sessions with refuse-on-collision semantics
in `bridge/dispatch!`.

## Scope

### Crew config: `:max-in-flight`

Add an optional field to crew config:

```clojure
{:crews
 {:alice {:tags          [:role/worker]
          :max-in-flight 3                ;; default 1
          ...}}}
```

Schema: positive integer, default 1 when absent. Validated at
config load. Default 1 preserves today's implicit
single-turn-at-a-time behavior.

### Session store: in-flight tracker

Sessions gain a transient `in-flight?` state, observable via the
session store. Not persisted — in-process atom keyed by session
id. On Isaac restart, the set resets cleanly (no real turn could
survive a crash).

**Invariant: one turn per session at a time.** Two concurrent
turns on the same session would interleave transcripts, collide
on tool side-effects, race compaction, and break the
chat-completions / Responses API contract (assistant ↔ tool
sequencing). The session store enforces this through atomic
claim semantics on `mark-in-flight!`. Callers must check the
return value before running the turn.

New API on `isaac.session.store`:

- `(mark-in-flight! store sess-id)` → boolean — atomically claims
  the slot. Returns `true` if it was free and is now marked,
  `false` if already in-flight (caller must refuse to dispatch).
- `(clear-in-flight! store sess-id)` — unset (no-op if not set).
- `(in-flight? store sess-id)` → boolean — query without claiming.
- `(in-flight-count store crew-name)` → number — for capacity
  checks.
- `(can-dispatch? store crew-name)` → boolean — true if
  `(in-flight-count store crew-name) < (max-in-flight-of
  crew-name)`.

### Bridge dispatch lifecycle

`bridge/dispatch!` is the single entry point for starting turns
(cron already uses it; Hail will too). It atomically claims the
session via `mark-in-flight!` before running the turn. If the
claim fails, it refuses to dispatch with a logged warning and
returns a structured refusal:

```clojure
(if (mark-in-flight! store sess-id)
  (try
    (run-turn ...)
    (finally
      (clear-in-flight! store sess-id)))
  (do
    (log/warn :dispatch/refused
              {:reason  :session-in-flight
               :session sess-id})
    {:dispatched? false :reason :session-in-flight}))
```

`finally` clears on exception. Process crash resets the map —
also safe. Each caller decides how to handle refusal — see below.

### Refusal handling is caller-driven

`bridge/dispatch!` does not queue. Each caller owns its own
re-dispatch policy:

- **CLI/ACP**: on refusal, the comm decides whether to surface
  "session busy" to the user, hold the typed input until the
  session is free, or simply discard. The choice depends on
  comm UX. Out-of-scope for this bean.
- **Hail**: refusal → leave the hail pending for next-tick retry.
- **Cron**: refusal → log and skip this window.

### Capacity check is caller-driven

Crew-level `:max-in-flight` is also not enforced inside
`dispatch!`. Callers (Hail's fan-out, cron) consult
`can-dispatch?` themselves to decide where to route work — e.g.,
Hail prefers an idle session on an under-capacity crew; if every
candidate crew is at capacity, the caller defers. The dispatcher
sees only the session-level invariant; crew-level shaping is the
caller's concern.

### Sessions CLI

Split into a follow-on bean (`isaac sessions` ✈️ marker and
`--in-flight` / `--not-in-flight` filter flags) that depends on
this bean. See `features/session/cli.feature` @wip scenarios.

## Out of scope (deferred)

- **File-based locks for multi-process.** v1 assumes one Isaac
  process per install. Promote `in-flight` to a lock file under
  `state-dir` if concurrent processes need to coordinate.
- **Per-tag-set concurrency caps** (e.g., "max 2 chess sessions,
  max 1 poker session" on one crew). Future field if needed; v1
  caps are crew-wide.
- **CLI prompt/chat going through the bridge.** If they sidestep
  the bridge today, they sidestep the in-flight tracker. Bringing
  them through is a separate bean if needed.
- **Dispatcher-side queueing.** Considered and rejected: each
  caller already has its own input source (CLI prompt buffer,
  Hail pending state, cron schedule). Adding a queue inside the
  dispatcher would duplicate them and force one UX on every
  caller. Refuse + caller-handles is the right seam.
- **Comm UX for refusal.** Whether CLI repopulates the prompt,
  shows "session busy," or holds the input is a comm-layer
  decision tracked separately.

## Acceptance

- Crew config schema accepts `:max-in-flight N` (positive int,
  default 1); meta-test for schema ownership passes (unit spec
  under `spec/isaac/config/`).
- Session store exposes the five functions above; `mark-in-flight!`
  has atomic claim semantics (returns `false` if already
  in-flight). Unit spec covers every store impl.
- `can-dispatch?` returns false at capacity, true under (unit
  spec).
- `bridge/dispatch!` consults `mark-in-flight!`, brackets every
  successful claim with mark/clear (including the exception path),
  and refuses on collision with `:dispatch/refused` log + return
  `{:dispatched? false :reason :session-in-flight}`.
- Feature scenarios under `features/session/concurrency.feature`
  pass with `@wip` removed:
  - Turn marks its session in-flight while running; clears on end.
  - Second dispatch on the **same session** is refused with
    `:dispatch/refused` log.
  - In-flight state clears after a turn errors.
- Run: `bb features features/session/concurrency.feature`

### New gherclj steps this bean must add

- `the turn ends on session "<id>"` (When) — releases the Grover
  wait gate on the in-flight turn and waits for that turn to
  finish.
- `session "<id>" in-flight status is true|false` (Then) — asserts
  via `in-flight?`.
- `dispatch is refused with reason "<reason>"` (Then) — asserts
  the last `bridge/dispatch!` returned
  `{:dispatched? false :reason <kw>}`.

### Existing step extensions

- New column `wait` on `the following model responses are queued:`
  — when `true`, Grover blocks on that response until released by
  `the turn ends on session "<id>"`. Default `false`.
- New value `error` for `type` column — Grover throws/returns an
  error result with `content` as the message.
- Contract change on `the user sends "<text>" on session "<id>"`:
  returns when the turn reaches a stable state (completed, gated
  at a Grover `wait`, or refused). Records the dispatch result so
  `dispatch is refused with reason "..."` can assert on it.
  Existing tests unaffected (no `wait`, no collision → same
  behavior as today).

## Relationship to other beans

- **Blocks**: the CLI affordances bean (✈️ + filter flags) and
  isaac-ugx7 (Hail) — both depend on the in-flight tracker and
  `can-dispatch?` query.
- **Independent of the tagging epic.** Can ship in parallel; no
  shared schema changes.
