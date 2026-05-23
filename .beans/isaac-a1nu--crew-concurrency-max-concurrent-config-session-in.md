---
# isaac-a1nu
title: 'Crew concurrency: max-concurrent config + session in-flight tracking'
status: todo
type: feature
created_at: 2026-05-23T00:24:48Z
updated_at: 2026-05-23T00:24:48Z
---

## Motivation

Today every crew implicitly runs one turn at a time, and there's no
observable signal of "is this session currently in a turn?" Hail's
fan-out slice depends on both:

- Picking an idle session over a busy one when multiple match a
  subscription's tags.
- Capping the number of simultaneous sessions a crew can run, so a
  queue-group hail (or a cron + hail collision) doesn't spin up
  unbounded parallel work.

This bean adds two small primitives, both useful beyond Hail:
declarative concurrency caps in crew config, and observable
in-flight state on sessions.

## Scope

### Crew config: `:max-concurrent`

Add an optional field to crew config:

```clojure
{:crews
 {:alice {:tags           [:role/worker]
          :max-concurrent 3                ;; default 1
          ...}}}
```

Schema: positive integer, default 1 when absent. Validated at config
load. Default 1 preserves today's implicit single-turn-at-a-time
behavior.

### Session store: in-flight tracker

Sessions gain a transient `in-flight?` state, observable via the
session store. Not persisted — in-process atom keyed by session id.
On Isaac restart, the set resets cleanly (no real turn could survive
a crash).

**Invariant: one turn per session at a time.** Two concurrent turns
on the same session would interleave transcripts, collide on tool
side-effects, race compaction, and break the chat-completions /
Responses API contract (assistant ↔ tool sequencing). The session
store enforces this through atomic claim semantics on
`mark-in-flight!` — callers must check the return value before
running the turn.

New API on `isaac.session.store`:

- `(mark-in-flight! store sess-id)` → boolean — atomically claims
  the slot. Returns `true` if it was free and is now marked,
  `false` if already in-flight (caller must refuse to dispatch).
- `(clear-in-flight! store sess-id)` — unset (no-op if not set).
- `(in-flight? store sess-id)` → boolean — query without claiming.
- `(in-flight-count store crew-name)` → number — for capacity checks.

### Bridge dispatch lifecycle

`bridge/dispatch!` is the single entry point for starting turns
(cron already uses it; Hail will too). It must atomically claim the
session via `mark-in-flight!` before running the turn. If the claim
fails (session already in flight), it refuses to dispatch with a
logged warning rather than overwriting in-flight state:

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

`finally` clears on exception. Process crash resets the whole map —
also safe.

### Capacity check

```clojure
(can-dispatch? store crew-name) → boolean
;; true if (in-flight-count store crew-name) < (max-concurrent-of crew-name)
```

Callers (cron, Hail's fan-out) consult before starting a turn on a
new or matched session. If false, the caller defers — Hail leaves
the hail in pending for next-tick retry.

### Sessions CLI: in-flight visibility

The `isaac sessions` listing gains two affordances backed by the
in-flight tracker:

1. **Visual marker** — sessions currently in flight render with an
   ✈️ adjacent to their identity in the listing. Idle sessions show
   no marker.
2. **Filter flags** — `--in-flight` (only in-flight) and
   `--not-in-flight` (only idle). Mutually exclusive; passing both
   errors with a clear message.

Backed by `in-flight?` reading the current atom snapshot. No
persistence needed.

## Out of scope (deferred)

- **File-based locks for multi-process.** v1 assumes one Isaac
  process per install. Promote `in-flight` to a lock file under
  `state-dir` if concurrent processes need to coordinate.
- **Per-tag-set concurrency caps** (e.g., "max 2 chess sessions, max
  1 poker session" on one crew). Future field if needed; v1 caps
  are crew-wide.
- **CLI prompt/chat going through the bridge.** If they sidestep the
  bridge today, they sidestep the in-flight tracker. Bringing them
  through is a separate bean if needed.

## Acceptance

- Crew config schema accepts `:max-concurrent N` (positive int,
  default 1) and the meta-test for schema ownership passes.
- Session store exposes the four functions above; `mark-in-flight!`
  has atomic claim semantics (returns `false` if already in flight).
- `bridge/dispatch!` consults `mark-in-flight!` and refuses to
  dispatch (logging `:dispatch/refused`) when the claim fails.
- `bridge/dispatch!` brackets every successful claim with
  mark/clear, including the exception path.
- `can-dispatch?` returns false at capacity, true under.
- `isaac sessions list` displays ✈️ next to in-flight sessions and
  supports mutually-exclusive `--in-flight` / `--not-in-flight`
  filter flags.
- Feature scenarios under `features/session/concurrency.feature`
  cover:
  - Turn marks its session in-flight while running.
  - Second dispatch on the **same session** is rejected even when
    the crew is under `:max-concurrent` (session-level invariant).
  - Second dispatch on the same crew, different session, is
    rejected when crew is at capacity (max-concurrent=1, one
    turn in-flight).
  - Second dispatch on the same crew, different session, is
    allowed under capacity (max-concurrent=3, one turn in-flight).
  - In-flight state clears after turn ends (success and exception
    paths).
- Feature scenarios under `features/session/cli.feature` (or
  extending the existing sessions CLI features) cover:
  - In-flight sessions render with ✈️.
  - `--in-flight` filters to in-flight sessions only.
  - `--not-in-flight` filters to idle sessions only.
  - Passing both flags errors clearly.

## Relationship to other beans

- **Used by isaac-ugx7 (Hail).** Wake/fan-out slice depends on
  in-flight observability and `can-dispatch?` for capacity gating.
- **Independent of the tagging epic.** Can ship in parallel; no
  shared schema changes.
